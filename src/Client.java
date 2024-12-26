import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;

public class Client extends JFrame {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 80;
    private static final String BASE_DOWNLOADS_DIR = "./users_files";

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private DataInputStream dataIn;
    private DataOutputStream dataOut;

    private JTextArea chatArea;
    private JTextField messageField;
    private JList<String> userList;
    private DefaultListModel<String> userListModel;
    private JList<String> fileList;
    private DefaultListModel<String> fileListModel;
    private String userDownloadsDir;

    public Client() {
        super("Отправка изображений");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLayout(new BorderLayout());

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane chatScroll = new JScrollPane(chatArea);
        add(chatScroll, BorderLayout.CENTER);

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setPreferredSize(new Dimension(150, 0));
        add(userScroll, BorderLayout.EAST);

        fileListModel = new DefaultListModel<>();
        fileList = new JList<>(fileListModel);
        JScrollPane fileScroll = new JScrollPane(fileList);
        fileScroll.setPreferredSize(new Dimension(150, 0));
        add(fileScroll, BorderLayout.WEST);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        messageField = new JTextField();
        JButton sendButton = new JButton("Отправить");
        JButton refreshButton = new JButton("Обновить список");

        bottomPanel.add(messageField, BorderLayout.CENTER);
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(sendButton);
        buttonPanel.add(refreshButton);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        sendButton.addActionListener(e -> sendMessage());
        refreshButton.addActionListener(e -> sendCommand("/files"));
        messageField.addActionListener(e -> sendMessage());

        try {
            Files.createDirectories(Paths.get(BASE_DOWNLOADS_DIR));
        } catch (IOException e) {
            appendMessage("Ошибка при создании базовой директории: " + e.getMessage());
        }

        connectToServer();
    }

    private void connectToServer() {
        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            dataIn = new DataInputStream(socket.getInputStream());
            dataOut = new DataOutputStream(socket.getOutputStream());

            new Thread(() -> {
                try {
                    String name = JOptionPane.showInputDialog("Введите ваше имя:");
                    if (name != null && !name.trim().isEmpty()) {
                        userDownloadsDir = BASE_DOWNLOADS_DIR + "/" + name;
                        Files.createDirectories(Paths.get(userDownloadsDir));

                        out.println(name);
                        sendCommand("/files");
                    }

                    String serverMessage;
                    while ((serverMessage = in.readLine()) != null) {
                        if (serverMessage.startsWith("INCOMING_IMAGE")) {
                            receiveImage(serverMessage);
                        } else if (serverMessage.startsWith("Список доступных файлов:")) {
                            fileListModel.clear();
                        } else if (serverMessage.matches("\\d+\\..+")) {
                            fileListModel.addElement(serverMessage);
                        } else {
                            appendMessage(serverMessage);
                        }
                    }
                } catch (IOException e) {
                    appendMessage("Соединение с сервером потеряно.");
                }
            }).start();

        } catch (IOException e) {
            appendMessage("Ошибка подключения к серверу: " + e.getMessage());
        }
    }

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            out.println(message);
            appendMessage("Вы: " + message);
            messageField.setText("");
        }
    }

    private void sendCommand(String command) {
        out.println(command);
    }

    private void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(message + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    private void receiveImage(String serverMessage) {
        try {
            String[] parts = serverMessage.split(" ", 3);
            String fileName = parts[1];
            String sender = parts[2];

            int fileSize = dataIn.readInt();
            if (fileSize <= 0 || fileSize > 100_000_000) {
                appendMessage("Ошибка: некорректный размер файла");
                return;
            }

            byte[] fileData = new byte[fileSize];
            int bytesRead = 0;
            while (bytesRead < fileSize) {
                int count = dataIn.read(fileData, bytesRead, fileSize - bytesRead);
                if (count < 0) {
                    throw new IOException("Преждевременный конец файла");
                }
                bytesRead += count;
            }

            Path filePath = Paths.get(userDownloadsDir, fileName);
            Files.write(filePath, fileData);

            appendMessage(sender + " отправил вам картинку! Файл сохранен в " + filePath.toString());
        } catch (IOException e) {
            appendMessage("Ошибка при получении файла: " + e.getMessage());
        }
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new Client().setVisible(true);
        });
    }
}