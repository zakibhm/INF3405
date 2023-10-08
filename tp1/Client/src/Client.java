
import java.net.*;
import java.nio.file.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import java.io.*;
import java.util.Scanner;

public class Client {

	private static Socket socket;
	private static String serverAddress = null;
	private static int serverPort = 0;
	private static Scanner scanner = new Scanner(System.in);
	private static DataOutputStream out;

	private static final String IP_ADDRESS_PATTERN = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";

	private static final Pattern pattern = Pattern.compile(IP_ADDRESS_PATTERN);

	public static boolean validateIPAddress(String ipAddress) {
		Matcher matcher = pattern.matcher(ipAddress);
		return matcher.matches();
	}

	public static boolean validatePort(int port) {
		if (port >= 5000 && port <= 5050)
			return true;
		return false;
	}

	static void readServerInfo() {
		boolean isValid = false;
		while (!isValid) {
			System.out.println("Please enter the server's IP address: ");
			serverAddress = scanner.nextLine();
			if (validateIPAddress(serverAddress)) {
				isValid = true;
			} else {
				System.out.println("Invalid IP address! Please try again.");
			}
		}

		isValid = false;

		while (!isValid) {
			System.out.println("Please enter the server's port number: ");
			serverPort = scanner.nextInt();
			if (validatePort(serverPort)) {
				isValid = true;
			} else {
				System.out.println("Invalid port number! Please try again.");
			}
		}

	}

	static boolean loggingToServer() {

		try {
			socket = new Socket(serverAddress, serverPort);
			out = new DataOutputStream(socket.getOutputStream());
			System.out.format("Connecting to the server %s:%d%n", serverAddress, serverPort);
			scanner.nextLine();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

		System.out.println("Please enter your username: ");
		String username = scanner.nextLine();

		System.out.println("Please enter your password: ");
		String password = scanner.nextLine();

		// Send username and password to the server
		try {
			out.writeUTF(username);
			out.writeUTF(password);
		} catch (IOException e) {
			System.out.println("Could not send username and password to the server");
			return false;
		}
		String serverAnswer = null;
		try {
			DataInputStream in = new DataInputStream(socket.getInputStream());
			serverAnswer = in.readUTF();
		} catch (IOException e) {
			System.out.println("Could not read an answer from the server");
			return false;
		}
		return loggingVerification(serverAnswer);
	}

	// Reading the server answer and verifying it
	static boolean loggingVerification(String answer) {
		if (answer.equals("SUCCESS")) {
			System.out.println("Authentication successful.\n");
			return true;

		} else if (answer.equals("NEW")) {
			System.out.println("Creating new account.\n");
			return true;
		} else {
			System.out.println("Error in password entry.\n Connection rejected");
			return false;
		}

	}

	static Object[] readImage() {

		System.out.println("Connecting to the image processing service.");

		Object[] result = new Object[2];
		System.out.print("Veuillez entrer le nom de l'image que vous voulez l'envoyer : \n");
		String imageName = scanner.nextLine();
		result[0] = imageName;

		String imagePath = Paths.get(imageName).toAbsolutePath().toString();

		File file = new File(imagePath);
		result[1] = file;
		return result;
	}

	static void sendImagetoServer(String imageName, File file) {
		try {
			// Send the image file name to the server
			out.writeUTF(imageName);

			// Read the image file as binary data
			byte[] imageData = new byte[(int) file.length()];
			try {

				FileInputStream fis = new FileInputStream(file);
				fis.read(imageData);

				// Send the length of the image data first
				out.writeInt(imageData.length);

				// Send the image data
				out.write(imageData);
				System.out.println("Image was sent successfully to the server");
				fis.close();
			} catch (IOException e) {
				System.out.println("Could not send Image");
			}

		} catch (IOException e) {
			System.out.println("Could not send Image name");
		}
	}

	static void readSobelImage() {
		try {
			DataInputStream in = new DataInputStream(socket.getInputStream());

			String sobelImageName = in.readUTF();
			int imageDataLength = in.readInt();
			byte[] imageData = new byte[imageDataLength];
			in.readFully(imageData);
			in.close();
			System.out.println("Sobel image received from the server");

			// Construct the full path of the Sobel image
			File sobelImageFile = new File(sobelImageName);
			String sobelImagePath = sobelImageFile.getAbsolutePath();

			// Get the parent directory (folder) path
			String parentFolderPath = new File(sobelImagePath).getParent();

			// Save the new sobel image to a file
			try {
				FileOutputStream fos = new FileOutputStream(sobelImageFile);
				fos.write(imageData);
				fos.close();

				System.out.println("Sobel image saved as: " + sobelImageName + " in " + parentFolderPath);
			} catch (IOException e) {
				e.printStackTrace();
			}

		} catch (IOException e) {
			System.out.println("Could not read an answer from the server");
		}
	}

	// this function is used to hanlde all image operations between the client and
	// the server
	static void ImageHandler() {

		// reading image from the user
		Object[] imageInfo = readImage();

		// sending image to the server
		sendImagetoServer((String) imageInfo[0], (File) imageInfo[1]);

		// receiving sobel image from the server
		readSobelImage();

	}

	public static void main(String[] args) throws Exception {

		// enrtering server information
		readServerInfo();

		// logging to the server
		if (loggingToServer()) {
			// sending image to the server
			ImageHandler();
		}

		out.flush();
		out.close();
		scanner.close();
		socket.close();
	}
}