package com.expensebook.web;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.net.URI;

public class WebApp {

    public static void main(String[] args) {
        int port = 8080;

        // 1. Priority: PORT environment variable (standard in Railway, Render, Heroku, Fly.io, Cloud Run)
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.trim().isEmpty()) {
            try {
                port = Integer.parseInt(envPort.trim());
            } catch (NumberFormatException ignored) {}
        } else {
            // 2. System property server.port
            String sysPort = System.getProperty("server.port");
            if (sysPort != null && !sysPort.trim().isEmpty()) {
                try {
                    port = Integer.parseInt(sysPort.trim());
                } catch (NumberFormatException ignored) {}
            } else if (args.length > 0) {
                // 3. Command line argument
                try {
                    port = Integer.parseInt(args[0]);
                } catch (NumberFormatException ignored) {}
            }
        }

        try {
            WebServer server = new WebServer(port);
            server.start();

            String url = "http://localhost:" + port;
            System.out.println("🚀 ExPense Book is live at: " + url);

            // Attempt to open default browser only if desktop GUI environment is available
            try {
                if (!GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI(url));
                }
            } catch (Throwable ignored) {
                // Silently ignore in headless production cloud environments
            }

        } catch (Exception e) {
            System.err.println("Failed to start ExPense Book Web Application: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
