
package org.example.ui;

import org.example.controller.DownloadController;
import org.example.model.DownloadTask;

import java.util.Scanner;


public class UserInterface {

    private final DownloadController controller;

    public UserInterface(DownloadController controller) {
        this.controller = controller;
    }

    public void runConsole() {
        Scanner sc = new Scanner(System.in);

        System.out.println("Download Manager (console demo)");
        System.out.println("Commands: add <url> <file>, start <id>, pause <id>, resume <id>, stop <id>, seg <id>, speed <bytesPerSec>, exit");

        while (true) {
            System.out.print("> ");
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;
            if (line.equalsIgnoreCase("exit")) break;

            String[] parts = line.split("\\s+");
            try {
                switch (parts[0]) {
                    case "add" -> {
                        String url = parts[1];
                        String file = parts[2];
                        DownloadTask t = controller.addDownload(url, file);
                        System.out.println("Added task id=" + t.getId());
                    }
                    case "start" -> controller.start(Long.parseLong(parts[1]));
                    case "pause" -> controller.pause(Long.parseLong(parts[1]));
                    case "resume" -> controller.resume(Long.parseLong(parts[1]));
                    case "stop" -> controller.stop(Long.parseLong(parts[1]));
                    case "seg" -> controller.printSegments(Long.parseLong(parts[1]));
                    case "speed" -> controller.setSpeedLimit(Long.parseLong(parts[1]));
                    default -> System.out.println("Unknown command");
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

        System.out.println("Bye");
    }
}
