package client;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Properties;

import utilities.UsefulMethods;

public class Client {
	
	public static void main(String[] args) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader("resource/instruction.txt"));
		Client client = new Client();
		String line = null;
		try {
			while ((line = reader.readLine()) != null) {
				String parts[] = line.split("\\|");
				if(parts[0].toUpperCase().equals(("r").toUpperCase())) {
					client.readFromFile(parts[1], parts[2], Integer.parseInt(parts[3]));
				}
				else if(parts[0].toUpperCase().equals(("w").toUpperCase())) {
					String filename = parts[1];
					client.createFile(filename, parts[2]);
				}
				else if(parts[0].toUpperCase().equals(("a").toUpperCase())) {
					String filename = parts[1];
					client.appendToFile(filename, parts[2]);
				}
			}
		} finally {
			reader.close();
		}
	}

	private void createFile(String filename, String message) throws IOException {
		// consult m-server and create a file
		System.out.println("create request filename: "+filename);
		int serverNumber = Integer.parseInt(SetMetadataServer("create",filename));
		SetUpNetworking(serverNumber,filename, message);
	}

	private void appendToFile(String filename, String message) {
		System.out.println("read request filename : "+filename);
		String lastChunkInfo = null;
		filename = filename.split("\\.")[0];
		try {
			lastChunkInfo = SetMetadataServer("append", filename);
			String[] lastInfos = lastChunkInfo.split(":");
			String chunkName = lastInfos[0];
			int ServerNumber = Integer.parseInt(lastInfos[1]);
			SetUpAppendNetworking(chunkName, ServerNumber, message);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void readFromFile(String filename, String offset, int bytesToRead) throws IOException {
		//consult meta data server and read
		System.out.println("read request filename : "+filename+" offset : "+offset+ " bytesToRead : "+ bytesToRead);
		String[] chunks = filename.split("\\.");
		int chunkNumber = (Integer.parseInt(offset)/8192)+1;
		String chunkName = chunks[0]+"-"+chunkNumber;
		int seekPosition = Integer.parseInt(offset) % 8192;
		int serverNumber = Integer.parseInt(SetMetadataServer("read", chunkName));
		if(serverNumber == 0) {
			try {
				Thread.sleep(2000);
			} catch(Exception e) {
				e.printStackTrace();
			}
			serverNumber = Integer.parseInt(SetMetadataServer("read", chunkName));
		}
		
		// IF the read extends in more than one file
		if((seekPosition+(bytesToRead)) > 8192) {
			int otherBytesToRead = seekPosition+(bytesToRead) - 8192;
			bytesToRead = 8192 - seekPosition;
			int otherChunkNumber = chunkNumber+1;
			String otherChunkName = chunks[0]+otherChunkNumber;
			int otherServerNumber = Integer.parseInt(SetMetadataServer("read", otherChunkName));
			System.out.println("Main Chunk : "+serverNumber +" Other chunks : "+otherServerNumber);
			SetUpReadNetworking(otherServerNumber, otherChunkName, 0, otherBytesToRead);
		}
		try {
			Thread.sleep(2000);
		} catch(Exception e) {
			e.printStackTrace();
		}
		SetUpReadNetworking(serverNumber, chunkName, seekPosition, bytesToRead);
	}


	private void SetUpAppendNetworking(String chunkName, int serverNumber, String message) {
		Properties ServerPort = UsefulMethods.getUsefulMethodsInstance().getPropertiesFile("spec.properties");
		
		String serverName = ServerPort.getProperty("server"+serverNumber);
		String portString = ServerPort.getProperty("server"+serverNumber+"port");
		int port = Integer.parseInt(portString.trim());
		/*System.out.println("Connecting to "+serverName+".... with port ......"+port);*/
		
		Socket client = null;
		
		try {
			client = new Socket(serverName, port);
			PrintWriter out1 = new PrintWriter(client.getOutputStream(), true);
			out1.println("append:"+chunkName+":"+serverNumber+":"+message);
			client.close();out1.close();			
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void SetUpReadNetworking(int serverNumber, String filename, int seekPosition, int bytesToRead) {
		Properties ServerPort = UsefulMethods.getUsefulMethodsInstance().getPropertiesFile("spec.properties");
		
		String serverName = ServerPort.getProperty("server"+serverNumber);
		String portString = ServerPort.getProperty("server"+serverNumber+"port");
		int port = Integer.parseInt(portString.trim());
		/*System.out.println("Connecting to "+serverName+".... with port ......"+port);*/
		
		Socket client = null;
		
		try {
			client = new Socket(serverName, port);
			PrintWriter out1 = new PrintWriter(client.getOutputStream(), true);
			out1.println("read:"+filename+":"+serverNumber+":"+seekPosition+":"+bytesToRead);
			client.close();out1.close();			
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void SetUpNetworking(int serverNumber,String filename, String message) {
		
		Properties ServerPort = UsefulMethods.getUsefulMethodsInstance().getPropertiesFile("spec.properties");
		
		String serverName = ServerPort.getProperty("server"+serverNumber);
		String portString = ServerPort.getProperty("server"+serverNumber+"port");
		int port = Integer.parseInt(portString.trim());
		/*System.out.println("Connecting to "+serverName+".... with port ......"+port);*/
		
		Socket client = null;
		
		try {
			client = new Socket(serverName, port);
			PrintWriter out1 = new PrintWriter(client.getOutputStream(), true);
			out1.println("write:"+filename+":"+serverNumber+":"+message);
			client.close();out1.close();			
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private String SetMetadataServer(String action, String filename) throws IOException {
		
		String serverNumber = null;
		Properties ServerPort = UsefulMethods.getUsefulMethodsInstance().getPropertiesFile("spec.properties");
		
		String serverName = ServerPort.getProperty("metadataserver");
		String portString = ServerPort.getProperty("metadataport");//Integer.parseInt(args[1]);
		int port = Integer.parseInt(portString.trim());
		//System.out.println("Connecting to "+serverName+".... with port ......"+port);
		
		Socket client = null;
		
		try {
			client = new Socket(serverName, port);
			PrintWriter out = new PrintWriter(client.getOutputStream(), true);
			BufferedReader in =new BufferedReader(new InputStreamReader(client.getInputStream()));
			if(action.equalsIgnoreCase("create")) {
				out.println("create"+":"+filename);
				serverNumber = readResponse(client, in);
				return serverNumber;
			}
			else if(action.equalsIgnoreCase("append")) {
				out.println(action+":"+filename);
				serverNumber = readAppendResponse(client, in);
				return serverNumber;
			}
			else if(action.equalsIgnoreCase("read")) {
				out.println(action+":"+filename);
				serverNumber = readResponse(client, in);
				return serverNumber;
			}
			client.close();out.close();in.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		} 
		finally {
			//client.close();
		}
		return null;
	}
	
	private String readAppendResponse(Socket client, BufferedReader in) throws IOException {
		String userInput;

		while ((userInput = in.readLine()) != null) {
			System.out.println("Response from Append server:"+userInput+ " Time of Response : "+UsefulMethods.getUsefulMethodsInstance().getTime());
			return userInput;
		}
		return null;
	}

	public String readResponse(Socket client, BufferedReader in) throws IOException {
		String userInput;
		/*BufferedReader stdIn = new BufferedReader(new InputStreamReader(
				client.getInputStream()));*/

		while ((userInput = in.readLine()) != null) {
			System.out.println("Response from server:"+userInput+ " Time of Response : "+UsefulMethods.getUsefulMethodsInstance().getTime());
			String parts[] = userInput.split(":");
			//return Integer.parseInt(parts[1]);
			return parts[1];
		}
		return null;
	}
}
