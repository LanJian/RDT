import java.io.*;
import java.net.*;
import java.util.Timer;

public class sender{
  InetAddress host;
  int emuPort, ackPort;
  BufferedReader br;
  DatagramSocket sendSocket;
  DatagramSocket receiveSocket;
  Timer timer;

  int windowSize;
  List<packet> packets;
  packet curPacket;
  int i;
  boolean done;

  public sender(String h, String ep, String ap, String f){
    try {
      host = InetAddress.getByName(h);
      emuPort = Integer.parseInt(ep);
      ackPort = Integer.parseInt(ap);
      br = new BufferedReader(new FileReader(f));
      sendSocket = new DatagramSocket();
      receiveSocket = new DatagramSocket(ackPort);
      timer = new Timer();

      windowSize = 10;
      packets = new LinkedList<packet>();
      curPacket = null;
      i = 0;
      done = false;
    } catch (UnknownHostException e) {
      System.err.println("Don't know about host: " + h);
      System.exit(1);
    } catch(Exception e){
      //help text
      System.exit(1);
    }

  }
  
  private void transmit() throws Exception{

    while(!done){
      // transmit
      char[] buf = new char[500];
      if(br.read(buf, 0, 500)<500)
        done = true;
      packet p = packet.createPacket(i, new String(buf));
      curPacket = p;

      boolean sent = false;
      while(!sent) {
        if (packets.length() < windowSize){
          sendPackets(false);
          sent = true;
        } else {
          System.sleep(500);
        }
      }

      packets.add(p);
      //TODO start timer if not already started
      i++;
      i%=32;
  }

  private void listen() throws Exception{
    byte[] receiveData = new byte[512];

    while(!done){
      // get ack
      DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
      receiveSocket.receive(receivePacket);
      packet ack = packet.parseUDPdata(receivePacket.getData());
      if(ack.getType() == 0){
        System.out.println("Got ack: " + ack.getSeqNum());
        packet p = packets.peek();
        while(p.getSeqNum() != ack.getSeqNum()){
          packets.removeFirst();
          p = packets.peek();
        }

        if(!packets.isEmpty()){
          //TODO start timer
        } else {
          //TODO stop timer
        }
      }
    }
  }

  private void timerExpired(){
    sendPackets(true);
    //TODO restart timer
  }

  private void sendPackets(boolean sendAll){
    byte[] sendData = new byte[512];
    if(sendALL){
      for (packet p: packets){
        sendData = p.getUDPdata();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
            host, emuPort);
        sendSocket.send(sendPacket);
      }
    }else{
      sendData = curPacket.getUDPdata();
      DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
          host, emuPort);
      sendSocket.send(sendPacket);
    }
  }

  public void start() throws Exception{
    
    Thread listener = new Thread(){
      public void run(){
        listen();
      }
    }
    listener.start();

    transmit();

    // send EOT
    byte[] sendData = packet.createEOT(i).getUDPdata();
    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
        host, emuPort);
    sendSocket.send(sendPacket);

    br.close();
    sendSocket.close();
    receiveSocket.close();
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
