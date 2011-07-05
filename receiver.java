import java.io.*;
import java.net.*;

public class receiver{
  InetAddress host;
  int emuPort, ackPort;
  PrintWriter pw;
  DatagramSocket receiveSocket;
  DatagramSocket sendSocket;
  int expected;
  
  public receiver(String h, String ap, String ep, String f){
    try {
      host = InetAddress.getByName(h);
      emuPort = Integer.parseInt(ep);
      ackPort = Integer.parseInt(ap);
      pw = new PrintWriter(f);
      receiveSocket = new DatagramSocket(emuPort);
      sendSocket = new DatagramSocket();
      expected = 0;
    } catch (UnknownHostException e) {
      System.err.println("Don't know about host: " + h);
      System.exit(1);
    } catch(Exception e){
      //help text
      e.printStackTrace();
      System.exit(1);
    }

  }

  public void start() throws Exception{
    boolean done = false;
    byte[] receiveData = new byte[512];
    byte[] sendData = new byte[512];
    int lastGot = -1;

    while(!done){
      DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
      receiveSocket.receive(receivePacket);
      packet p = packet.parseUDPdata(receivePacket.getData());
    
      //we discard packet if not expected
      System.out.println("expected:" + expected);
      if(p.getSeqNum() == expected){
        System.out.println(p.getSeqNum());
        if(p.getType() == 2){
          done = true;
          packet ack = packet.createEOT(p.getSeqNum());
          sendData = ack.getUDPdata();
          DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
              host, ackPort);
          sendSocket.send(sendPacket);
        } else {
          lastGot = expected;
          expected++;
          expected%=32;

          pw.print(new String(p.getData()));
        }
      }

      if (!done){
        //send ACK for last received packet
        packet ack = packet.createACK(lastGot);
        sendData = ack.getUDPdata();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
            host, ackPort);
        sendSocket.send(sendPacket);
      }

    }

    pw.close();
    receiveSocket.close();
    sendSocket.close();
    System.out.println("done");
  }

  public static void main(String[] args){
    try{
      receiver r = new receiver(args[0], args[1], args[2], args[3]);
      r.start();
    }catch(Exception e){
      System.err.println("An error occured");
      System.exit(1);
    }
  }

}
