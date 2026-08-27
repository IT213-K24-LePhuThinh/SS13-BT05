package com.rikkei.express.config;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Tái sử dụng từ Bài 1 - Cấu hình hai MCP Server (Postgres + FileSystem)
 * bằng Stdio Transport trên Windows.
 */
@Configuration
public class McpClientConfig {

    private static final Logger log = LoggerFactory.getLogger(McpClientConfig.class);

    @Bean
    public McpSyncClient postgresMcpClient() {
        log.info("Bootstrapping Postgres MCP Client ...");
        ServerParameters params = new ServerParameters.Builder("cmd.exe")
                .args("/c", "npx.cmd",
                        "-y", "@modelcontextprotocol/server-postgres",
                        "postgresql://logistics:secret@localhost:5432/rikkei_logistics_db")
                .build();

        var transport = new StdioClientTransport.Builder(params)
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        McpSyncClient client = McpSyncClient.using(transport).build();
        log.info("Postgres MCP Client handshake completed.");
        return client;
    }

    @Bean
    public McpSyncClient filesystemMcpClient() {
        log.info("Bootstrapping FileSystem MCP Client ...");
        String baseDir = "C:\\data\\logistics";

        ServerParameters params = new ServerParameters.Builder("cmd.exe")
                .args("/c", "npx.cmd",
                        "-y", "@modelcontextprotocol/server-filesystem",
                        baseDir)
                .build();

        var transport = new StdioClientTransport.Builder(params)
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        McpSyncClient client = McpSyncClient.using(transport).build();
        log.info("FileSystem MCP Client handshake completed.");
        return client;
    }
}