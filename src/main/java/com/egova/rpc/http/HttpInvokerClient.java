package com.egova.rpc.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HttpsURLConnection;

import com.egova.rpc.exception.RemoteException;
import com.egova.rpc.support.HttpInvokerClientConfiguration;
import com.egova.rpc.support.RemoteInvocationResult;
import static com.egova.rpc.http.HttpConstants.*;

/**
 * 远程调用客户端,基于java序列化实现
 * @author Administrator
 *
 */
public class HttpInvokerClient {

	private boolean acceptGzipEncoding = true;

	private int connectTimeout = -1;

	private int readTimeout = -1;

	public void setConnectTimeout(int connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	public void setReadTimeout(int readTimeout) {
		this.readTimeout = readTimeout;
	}

	public boolean isAcceptGzipEncoding() {
		return acceptGzipEncoding;
	}

	public void setAcceptGzipEncoding(boolean acceptGzipEncoding) {
		this.acceptGzipEncoding = acceptGzipEncoding;
	}

	public RemoteInvocationResult doExecuteRequest(HttpInvokerClientConfiguration config, ByteArrayOutputStream baos)
			throws IOException, ClassNotFoundException {

		HttpURLConnection con = openConnection(config);
		prepareConnection(con, baos.size());
		writeRequestBody(con, baos);
		validateResponse(con);
		InputStream responseBody = readResponseBody(con);

		return readRemoteInvocationResult(responseBody);
	}

	protected HttpURLConnection openConnection(HttpInvokerClientConfiguration config) throws IOException {
		URL url = new URL(config.getServiceUrl());

		HttpURLConnection conn;

		if (HTTPS_PROTOCOL.equals(url.getProtocol())) {
			conn = openHttpsConnection(url);
		} else {
			conn = openHttpConnection(url);
		}

		return conn;
	}

	protected HttpURLConnection openHttpConnection(URL url) throws IOException {
		if (!HTTP_PROTOCOL.equals(url.getProtocol())) {
			throw new IOException("Service URL [" + url.toString() + "] is not an HTTP PROTOCOL");
		}

		URLConnection con = url.openConnection();
		if (!(con instanceof HttpURLConnection)) {
			throw new IOException("Service URL [" + url.toString() + "] is not an HTTP URL");
		}
		return (HttpURLConnection) con;
	}

	protected HttpsURLConnection openHttpsConnection(URL url) throws IOException {
		if (!HTTPS_PROTOCOL.equals(url.getProtocol())) {
			throw new IOException("Service URL [" + url.toString() + "] is not an HTTPS PROTOCOL");
		}

		URLConnection con = url.openConnection();
		if (!(con instanceof HttpsURLConnection)) {
			throw new IOException("Service URL [" + url.toString() + "] is not an HTTPS URL");
		}

		HttpsURLConnection connHttps = (HttpsURLConnection) con;
		connHttps.setSSLSocketFactory(HttpsHelper.socketFactory);
		connHttps.setHostnameVerifier(HttpsHelper.verifier);

		return connHttps;
	}

	protected void prepareConnection(HttpURLConnection connection, int contentLength) throws IOException {
		if (this.connectTimeout >= 0) {
			connection.setConnectTimeout(this.connectTimeout);
		}
		if (this.readTimeout >= 0) {
			connection.setReadTimeout(this.readTimeout);
		}
		connection.setDoOutput(true);
		connection.setRequestMethod(HTTP_METHOD_POST);
		connection.setRequestProperty(HTTP_HEADER_CONTENT_TYPE, CONTENT_TYPE_SERIALIZED_OBJECT);
		connection.setRequestProperty(HTTP_HEADER_CONTENT_LENGTH, Integer.toString(contentLength));

		if (isAcceptGzipEncoding()) {
			connection.setRequestProperty(HTTP_HEADER_ACCEPT_ENCODING, ENCODING_GZIP);
		}
	}

	protected void writeRequestBody(HttpURLConnection con, ByteArrayOutputStream baos) throws IOException {
		baos.writeTo(con.getOutputStream());
	}

	protected void validateResponse(HttpURLConnection con) throws IOException {

		if (con.getResponseCode() >= 300) {
			throw new IOException("Did not receive successful HTTP response: status code = " + con.getResponseCode()
					+ ", status message = [" + con.getResponseMessage() + "]");
		}
	}

	protected InputStream readResponseBody(HttpURLConnection con) throws IOException {

		if (isGzipResponse(con)) {
			return new GZIPInputStream(con.getInputStream());
		} else {
			return con.getInputStream();
		}
	}

	protected boolean isGzipResponse(HttpURLConnection con) {
		String encodingHeader = con.getHeaderField(HTTP_HEADER_CONTENT_ENCODING);
		return (encodingHeader != null && encodingHeader.toLowerCase().contains(ENCODING_GZIP));
	}

	protected RemoteInvocationResult readRemoteInvocationResult(InputStream is)
			throws IOException, ClassNotFoundException {

		ObjectInputStream ois = new ObjectInputStream(is);
		try {
			return doReadRemoteInvocationResult(ois);
		} finally {
			ois.close();
		}
	}

	protected RemoteInvocationResult doReadRemoteInvocationResult(ObjectInputStream ois)
			throws IOException, ClassNotFoundException {

		Object obj = ois.readObject();
		if (!(obj instanceof RemoteInvocationResult)) {
			throw new RemoteException("Deserialized object needs to be assignable to type ["
					+ RemoteInvocationResult.class.getName() + "]: " + obj);
		}
		return (RemoteInvocationResult) obj;
	}
}
