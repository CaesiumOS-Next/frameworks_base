package android.libcore.conscrypt;

import com.android.org.conscrypt.ChannelType;

import java.io.IOException;

/**
 * Utility for creating test client and server endpoints.
 */
interface EndpointFactory {
    ClientEndpoint newClient(ChannelType channelType, int port, String[] protocols,
            String[] ciphers) throws IOException;

    ServerEndpoint newServer(ChannelType channelType, int messageSize,
            String[] protocols, String[] ciphers) throws IOException;
}