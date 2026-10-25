package org.apache.kafka.common.requests;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.common.message.*;
import org.apache.kafka.common.protocol.ApiKeys;

/** Test only: two loopback brokers; the bootstrap advertises the second endpoint. No produce/consume API. */
public final class KafkaMetadataBrokerFixture {
    static final AtomicLong targetBytes = new AtomicLong(), targetConnections = new AtomicLong();
    static volatile boolean closing;
    static int advertisedPort;
    public static void main(String[] args) throws Exception {
        try (ServerSocket bootstrap = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"));
             ServerSocket advertised = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))) {
            advertisedPort = advertised.getLocalPort(); start(bootstrap, false); start(advertised, true);
            System.out.println("{\"bootstrapPort\":" + bootstrap.getLocalPort() + ",\"advertisedPort\":" + advertisedPort + "}");
            BufferedReader commands = new BufferedReader(new InputStreamReader(System.in));
            for (String line; (line = commands.readLine()) != null;) {
                if (line.equals("stats")) System.out.println("{\"connections\":" + targetConnections.get() + ",\"bytes\":" + targetBytes.get() + "}");
                else break;
            }
            closing = true;
        }
    }
    static void start(ServerSocket server, boolean target) {
        Thread thread = new Thread(() -> {
            while (!closing) try {
                Socket socket = server.accept(); if (target) targetConnections.incrementAndGet();
                Thread client = new Thread(() -> serve(socket, target)); client.setDaemon(true); client.start();
            } catch (IOException ended) { return; }
        }); thread.setDaemon(true); thread.start();
    }
    static void serve(Socket socket, boolean target) {
        try (Socket owned = socket) {
            owned.setSoTimeout(15000); DataInputStream input = new DataInputStream(owned.getInputStream());
            DataOutputStream output = new DataOutputStream(owned.getOutputStream());
            while (!closing) {
                int size = input.readInt(); if (size < 1 || size > 1048576) return;
                byte[] bytes = input.readNBytes(size); if (bytes.length != size) return;
                if (target) targetBytes.addAndGet(size + 4L);
                RequestHeader header = RequestHeader.parse(ByteBuffer.wrap(bytes)); AbstractResponse response;
                if (header.apiKey() == ApiKeys.API_VERSIONS) {
                    var versions = new ApiVersionsResponseData.ApiVersionCollection();
                    for (ApiKeys key : List.of(ApiKeys.API_VERSIONS, ApiKeys.METADATA, ApiKeys.DESCRIBE_CLUSTER))
                        versions.add(ApiVersionsResponse.toApiVersion(key));
                    response = new ApiVersionsResponse(new ApiVersionsResponseData().setErrorCode((short)0).setApiKeys(versions));
                } else if (header.apiKey() == ApiKeys.METADATA) {
                    var broker = new MetadataResponseData.MetadataResponseBroker().setNodeId(1).setHost("127.0.0.1").setPort(advertisedPort);
                    var brokers = new MetadataResponseData.MetadataResponseBrokerCollection(); brokers.add(broker);
                    response = new MetadataResponse(new MetadataResponseData().setBrokers(brokers).setClusterId("owned-test").setControllerId(1), header.apiVersion());
                } else if (header.apiKey() == ApiKeys.DESCRIBE_CLUSTER) {
                    var brokers = new DescribeClusterResponseData.DescribeClusterBrokerCollection();
                    brokers.add(new DescribeClusterResponseData.DescribeClusterBroker().setBrokerId(1).setHost("127.0.0.1").setPort(advertisedPort));
                    response = new DescribeClusterResponse(new DescribeClusterResponseData().setBrokers(brokers).setClusterId("owned-test").setControllerId(1));
                } else return;
                ByteBuffer frame = response.serializeWithHeader(header.toResponseHeader(), header.apiVersion());
                output.writeInt(frame.remaining()); byte[] body = new byte[frame.remaining()]; frame.get(body); output.write(body); output.flush();
            }
        } catch (Exception ignored) { /* Test peers close on completion or guarded denial. */ }
    }
}
