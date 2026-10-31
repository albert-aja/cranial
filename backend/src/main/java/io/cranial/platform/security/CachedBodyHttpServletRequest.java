package io.cranial.platform.security;

import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    public CachedBodyHttpServletRequest(HttpServletRequest request, int maxInputBytes) throws IOException {
        super(request);
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxInputBytes) {
            throw new CranialException(
                    ErrorCode.PAYLOAD_TOO_LARGE,
                    "Request payload exceeds application max_input_bytes limit (" + maxInputBytes + " bytes)"
            );
        }
        this.cachedBody = readBounded(request.getInputStream(), maxInputBytes);
    }

    public byte[] cachedBody() {
        return cachedBody;
    }

    private static byte[] readBounded(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int total = 0;
        int read;
        while ((read = in.read(chunk)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new CranialException(
                        ErrorCode.PAYLOAD_TOO_LARGE,
                        "Request payload exceeds application max_input_bytes limit (" + maxBytes + " bytes)"
                );
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream byteStream = new ByteArrayInputStream(cachedBody);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return byteStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }

            @Override
            public int read() {
                return byteStream.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
}

