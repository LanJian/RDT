import java.io.*;
import java.net.*;

/**
 * Receiver application
 * Implements an unbuffered receiver
 * Writes received data to a file
 */
public class receiver{
  InetAddress host;
  int emuPort, ackPort;
  PrintWriter pw, log;
  DatagramSocket receiveSocket;
  DatagramSocket sendSocket;
  int expected;
  
  public receiver(String h, String ap, String ep, String f){
    try {
      host = InetAddress.getByName(h);
      emuPort = Integer.parseInt(ep);
      ackPort = Integer.parseInt(ap);
      pw = new PrintWriter(f);
      log = new PrintWriter("arrival.log");
      receiveSocket = new DatagramSocket(emuPort);
      sendSocket = new DatagramSocket();
      expected = 0;
    } catch (UnknownHostException e) {
      System.err.println("Don't know about host: " + h);
      System.exit(1);
    } catch(Exception e){
      e.printStackTrace();
      System.exit(1);
    }

  }

  // called from main to start receiver process
  public void start() throws Exception{
    boolean done = false;
    byte[] receiveData = new byte[512];
    byte[] sendData = new byte[512];
    // lastGot used as seqnum when sending acks
    int lastGot = -1;

    while(!done){
      DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
      receiveSocket.receive(receivePacket);
      packet p = packet.parseUDPdata(receivePacket.getData());
    
      log.println(p.getSeqNum());

      //we discard packet if not expected
      if(p.getSeqNum() == expected){
        // if we receive EOT then we are done
        if(p.getType() == 2){
          done = true;
          packet ack = packet.createEOT(p.getSeqNum());
          sendData = ack.getUDPdata();
          DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
              host, ackPort);
          sendSocket.send(sendPacket);
        } else {
          // otherwise we have a data packet
          // in this case, write the data to file and expect the next packet
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
    log.close();
    receiveSocket.close();
    sendSocket.close();
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
