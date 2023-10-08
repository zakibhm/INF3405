
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

			System.out.print("Veuillez entrer l'addresse IP du serveur : \n");
			serverAddress = scanner.nextLine();
			if (validateIPAddress(serverAddress)) {
				isValid = true;
			} else {
				System.out.print("Addresse IP est invalide! \n");

			}

		}
		isValid = false;
		while (!isValid) {
			System.out.print("Veuillez entrer le numero de port du serveur: \n");
			serverPort = scanner.nextInt();
			if (validatePort(serverPort)) {
				isValid = true;
			} else {
				System.out.print("Numero de port est invalide! \n");
			}

		}

	}

	static boolean loggingToServer() {

		try {
			socket = new Socket(serverAddress, serverPort);
			out = new DataOutputStream(socket.getOutputStream());
			System.out.format("Connexion au serveur %s:%d%n", serverAddress, serverPort);
			scanner.nextLine(); // Consume the newline character
		} catch (IOException e) {
			// Handle other IO-related exceptions
			e.printStackTrace();
			return false;
		}

		System.out.print("Veuillez entrer votre nom d'utilisateur : \n");
		String username = scanner.nextLine();

		System.out.print("Veuillez entrer votre mot de passe : \n");
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

			// verify the User information
			loggingVerification(serverAnswer);
		} catch (IOException e) {
			System.out.println("Could not read an answer from the server");
			return false;
		}
		return true;
	}

	static void loggingVerification(String answer) {
		if (answer.equals("SUCCESS")) {
			System.out.println("Authentication successful.\n");

		} else if (answer.equals("NEW")) {
			System.out.println("Authentication successful.\n");
		} else {
			System.out.println("Error in password entry.\n Connection rejected");

			try {
				socket.close();// Close the socket if authentication fails
			} catch (IOException e) {
				System.out.println("Could not close the socket");
			}
		}
		System.out.println("Connexion au service de traitment d'image.\n");
	}

	static Object[] readImage() {
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