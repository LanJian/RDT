import java.io.*;
import java.net.*;

public class sender{
  InetAddress host;
  int emuPort, ackPort;
  BufferedReader br;
  DatagramSocket sendSocket;
  DatagramSocket receiveSocket;

  public sender(String h, String ep, String ap, String f){
    try {
      host = InetAddress.getByName(h);
      emuPort = Integer.parseInt(ep);
      ackPort = Integer.parseInt(ap);
      br = new BufferedReader(new FileReader(f));
      sendSocket = new DatagramSocket();
      receiveSocket = new DatagramSocket(ackPort);
      receiveSocket.setSoTimeout(50);
    } catch (UnknownHostException e) {
      System.err.println("Don't know about host: " + h);
      System.exit(1);
    } catch(Exception e){
      //help text
      System.exit(1);
    }

  }

  public void start() throws Exception{
    boolean done = false;
    byte[] sendData = new byte[512];
    byte[] receiveData = new byte[512];

    int i = 0;

    while(!done){
      char[] buf = new char[500];
      if(br.read(buf, 0, 500)<500)
        done = true;
      packet p = packet.createPacket(i, new String(buf));
      sendData = p.getUDPdata();
      DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
          host, emuPort);

      boolean acked = false;
      while(!acked){
        sendSocket.send(sendPacket);

        try{
          DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
          receiveSocket.receive(receivePacket);
          packet ack = packet.parseUDPdata(receivePacket.getData());
          if(ack.getType() == 0 && ack.getSeqNum() == i){
            acked = true;
            System.out.println("Got ack: " + ack.getSeqNum());
          }
        } catch(SocketTimeoutException e){
        }
      }

      i++;
      i%=32;
    }

    sendData = packet.createEOT(i).getUDPdata();
    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
        host, emuPort);
    sendSocket.send(sendPacket);

    br.close();
  }

  public static void main(String[] args){
    try{
      sender s = new sender(args[0], args[1], args[2], args[3]);
      s.start();
    }catch(Exception e){
      System.err.println("An error occured");
      System.exit(1);
    }
  }
}
