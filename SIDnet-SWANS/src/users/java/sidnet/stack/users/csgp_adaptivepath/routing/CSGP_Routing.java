/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sidnet.stack.users.csgp_adaptivepath.routing;
import java.util.LinkedList;
import jist.runtime.JistAPI;
import jist.swans.Constants;
import sidnet.core.interfaces.AppInterface;
import jist.swans.mac.MacAddress;
import jist.swans.misc.Message;
import jist.swans.net.NetAddress;
import jist.swans.net.NetInterface;
import jist.swans.net.NetMessage;
import jist.swans.route.RouteInterface;
import sidnet.colorprofiles.ColorProfileGeneric;
import sidnet.core.misc.Location2D;
import sidnet.core.misc.Node;
import sidnet.core.misc.NodeEntry;
import sidnet.core.misc.Reason;
import sidnet.core.misc.Region;
import sidnet.stack.users.csgp_adaptivepath.app.MessageDataValue;
import sidnet.stack.users.csgp_adaptivepath.app.MessageQuery;
import sidnet.stack.std.routing.shortestgeopath.SGPWrapperMessage;       
/**
 *
 * @author Maira_Fakhri
 */

public class CSGP_Routing implements RouteInterface{
    public static final byte ERROR=-1;
    public static final byte SUCCESS=0;
    
    private final Node myNode; //the sidnet handle to the node representation
    
//Entry hook-up(newtwork stack)
/**Network Entity*/    
    private NetInterface netEntity;

/*Self referencing proxy entity*/
    private RouteInterface self;
    
/*the proxy entity for this application interface*/
    private AppInterface appInterface;
    
//DO NOT MAKE THIS STATIC
    private ColorProfileGeneric colorProfileGeneric = new ColorProfileGeneric();
    
/**Creates a new instance of ShortestGeographicalpathRouting
 * @param Node --> the sidnet node handle to acces its GUI primitives and share Environment
    */

    public CSGP_Routing(Node myNode){
        this.myNode=myNode;

//Create aProxy  for the application layer of this node
        self=(RouteInterface)JistAPI.proxy(this, RouteInterface.class);     
    }
/**SWANS Lagency. WE ni longer Enable This*/
    public void peek(NetMessage msg, MacAddress lastHopMac){
        //nopeeking
    }

/**Receive a message from the network layer
 * This  nodes method is typically called when this node is the ultimate detination of an incomming data message(the sink)
 * 
 * @param Message--> the incoming Message
 * @param NetAddress --> the Original Source of the message
 * @param MacAddress--> the mac address 1-hop neighbor from which this nodes received this message
 * @param macId --> the mac Id interface through which this message  was received
 * @param NetAddress --> the IP address of the ultimate node destination(sink)
 * @param priority --> the priority of the incoming message
 * @patam ttl -->Time to Leave
 */
    public void receive(Message msg, NetAddress src, MacAddress lastHop,byte macId, NetAddress dest, byte priority, byte ttl){
        if(myNode.getEnergyManagement()
                .getBattery()
                .getPercentageEnergyLevel()<2)
            return;

//Provide a basic visual feedback on the fact that this node has receive message
        myNode.getNodeGUI().colorCode.mark(colorProfileGeneric,
                                            ColorProfileGeneric.RECEIVE, 20);
        
//A message may come in a format that you define based on your implementation
//you must extract that format and act upon
        if(msg instanceof CSGPWrapperMessage){
            CSGPWrapperMessage msgNZ=(CSGPWrapperMessage)msg;
        sendToAppLayer(msgNZ.getPayload(),null);
        System.out.println("Node "+myNode.getIP()+" has received a message");
    }
        //otherwise, it is a format not recognize by sgp o we ignore it.
    }
/**
* Send a message
* This method is being called when a message, 
* coming from either the application layer or the mac layer,
* needs to be forwarded
*
* @param  NetMessage  the 'NetMessage' wrapped Message
*/    
    public void send(NetMessage msg){
//process only if there are energy reserves
     if(myNode.getEnergyManagement().getBattery().getPercentageEnergyLevel()<2)
         return;
//update visual
     myNode.getNodeGUI().colorCode.mark(colorProfileGeneric, ColorProfileGeneric.RECEIVE, 2000);

//if this message come from App Layer
     if(!(((NetMessage.Ip)msg).getPayload()instanceof CSGPWrapperMessage))
         return; //ignore non specified message
     
//extract message
     Location2D targetLocation=null;
     CSGPWrapperMessage msgSGP=
                        (CSGPWrapperMessage)((NetMessage.Ip)msg).getPayload();
//seek destination information(Location or region based)
     if (msgSGP.getTargetRegion()!=null)
         targetLocation=extractClosestVertex(msgSGP.getTargetRegion()); 
     if (msgSGP.getTargetLocation()!=null)
         targetLocation=msgSGP.getTargetLocation();
     if(msgSGP.getPayload() instanceof MessageQuery){
         handleQueryMessage(msg); 
     } else if(msgSGP.getPayload() instanceof MessageDataValue){
         System.out.println("Node "+myNode.getID()+" Get data value message");
         handleWithTargetLocationToCH(targetLocation, msg);    
     } else {
         System.out.println("Node "+myNode.getID()+" Get unknown message");
     }
    }    
private void handleQueryMessage(NetMessage msg){
         CSGPWrapperMessage msgAGW=(CSGPWrapperMessage)((NetMessage.Ip)msg).getPayload();
    NetMessage.Ip copyOfMsg=new NetMessage.Ip(msgAGW, ((NetMessage.Ip)msg).getSrc(),
                                 ((NetMessage.Ip)msg).getDst(),
                                 ((NetMessage.Ip)msg).getProtocol(),
                                 ((NetMessage.Ip)msg).getPriority(),
                                 ((NetMessage.Ip)msg).getTTL(),
                                 ((NetMessage.Ip)msg).getId(),
                                 ((NetMessage.Ip)msg).getFragOffset());

//kirim kesemua node tetangga  
    sendToLinkLayer(copyOfMsg, NetAddress.ANY); 

//node yang menerima pesan ini akan melakukan  sensing dan kirim ke app layer
    if(this.myNode.getIP()!=((NetMessage.Ip)msg).getSrc())
        sendToAppLayer(msgAGW.getPayload(),null);  
     }
     

    private void handleWithTargetLocation(Location2D targetLocation, NetMessage msg)
    {
            SGPWrapperMessage msgSGP=(SGPWrapperMessage)((NetMessage.Ip)msg).getPayload();
            
    //Retrieve the ip address of the 10hop neighbor closest to the area of interest.
            NetAddress nextHopIP=getThroughShortestPath(targetLocation); 
    
    //if there is no node closer to the area of interest than this node than this node will get the message
        if(nextHopIP.hashCode()==myNode.getIP().hashCode())
            sendToAppLayer(msgSGP.getPayload(),null); 
        else {  //keep forwarding, fisrt make a copy message 
                    NetMessage.Ip copyOfMsg=new NetMessage.Ip(msgSGP, ((NetMessage.Ip)msg).getSrc(),
                                            ((NetMessage.Ip)msg).getDst(),
                                            ((NetMessage.Ip)msg).getProtocol(),
                                            ((NetMessage.Ip)msg).getPriority(),
                                            ((NetMessage.Ip)msg).getTTL(),
                                            ((NetMessage.Ip)msg).getId(),
                                            ((NetMessage.Ip)msg).getFragOffset());
                    sendToLinkLayer(copyOfMsg, nextHopIP); 
                    }
    } 
    private void handleWithTargetLocationToCH(Location2D targetLocation, NetMessage msg)
    {
        CSGPWrapperMessage msgSGP=(CSGPWrapperMessage)((NetMessage.Ip)msg).getPayload();
        
    //Retrieve the ip address of the 10hop neighbor closest to the area of interest.
        NetAddress nextHopIP;
        nextHopIP = getThroughShortestPath(targetLocation);
        if(msgSGP.getStatus()==false){
            nextHopIP=SearchChIPAddress();
            NetMessage.Ip copyOfMsg=new NetMessage.Ip(msgSGP, ((NetMessage.Ip)msg).getSrc(),
                                            ((NetMessage.Ip)msg).getDst(),
                                            ((NetMessage.Ip)msg).getProtocol(),
                                            ((NetMessage.Ip)msg).getPriority(),
                                            ((NetMessage.Ip)msg).getTTL(),
                                            ((NetMessage.Ip)msg).getId(),
                                            ((NetMessage.Ip)msg).getFragOffset());
            sendToLinkLayer(copyOfMsg, nextHopIP); 
            msgSGP.setStatus();
            }
        else if(msgSGP.getStatus()==true){
            NetMessage.Ip copyOfMsg=new NetMessage.Ip(msgSGP, ((NetMessage.Ip)msg).getSrc(),
                                            ((NetMessage.Ip)msg).getDst(),
                                            ((NetMessage.Ip)msg).getProtocol(),
                                            ((NetMessage.Ip)msg).getPriority(),
                                            ((NetMessage.Ip)msg).getTTL(),
                                            ((NetMessage.Ip)msg).getId(),
                                            ((NetMessage.Ip)msg).getFragOffset());
            sendToLinkLayer(copyOfMsg, nextHopIP); 
            }
        else if(nextHopIP.hashCode()==myNode.getIP().hashCode()){
            sendToAppLayer(msgSGP.getPayload(),null);
            System.out.println("Message received at sink");
        }
        else {
            System.out.println("Unknown Destination");
        }
        }
/**Find the closest vertex 
 * a query may contain a region of interest since this is sgp routing
 * we look for to the closest vertex of that region
    */   
    private Location2D extractClosestVertex(Region targetRegion){
        targetRegion.resetIterator();
        Location2D nextLoc=targetRegion.getNext().convertTo(targetRegion.getLocationContext(),
                                                    myNode.getLocationContext());
        Location2D closestVertex=nextLoc;
        
        double distMin=nextLoc.distanceTo(myNode.getLocation2D());
        
        while(targetRegion.hasNext()){
            nextLoc=targetRegion.getNext();
            Location2D actualLoc=nextLoc.convertTo(targetRegion.getLocationContext(), 
                                                    myNode.getLocationContext());
            if(actualLoc.distanceTo(myNode.getLocation2D())<distMin){
                distMin=actualLoc.distanceTo(myNode.getLocation2D());
                closestVertex=actualLoc; 
            }
        }
        return closestVertex;
    }
   
    public void sendToAppLayer(Message msg, NetAddress src)
    {
     //ignore if not enough energy
        if(myNode.getEnergyManagement().getBattery().getPercentageEnergyLevel()<2)
            return;
        appInterface.receive(msg, src, null, (byte)-1, NetAddress.LOCAL, (byte)-1, (byte)-1);
    }
    
    public byte sendToLinkLayer(NetMessage.Ip ipMsg, NetAddress nextHopDestIP)
    {
        if(myNode.getEnergyManagement()
                 .getBattery()
                 .getPercentageEnergyLevel()<2)
            return 0;
        if(myNode.getID()==164)
            System.out.println("Route Packet to "+nextHopDestIP);
        if(nextHopDestIP==null)
            System.err.println("NULL nextHopDestIP");
        if(nextHopDestIP==NetAddress.ANY)
            netEntity.send(ipMsg, Constants.NET_INTERFACE_DEFAULT, MacAddress.ANY);
        else
        {
            NodeEntry nodeEntry=myNode.neighboursList.get(nextHopDestIP);
            if(nodeEntry==null)
            {
                System.err.println("Node #"+myNode.getID()+":Destination IP("+nextHopDestIP+") not in my neigborhood. Please Re-route, are you sending to yourself");
                System.err.println("Node #"+myNode.getID()+"has +"+myNode.neighboursList.size()+"neighbors");
                return ERROR;
            }
        MacAddress macAddress=nodeEntry.mac;
            if(macAddress==null)
            {
                 System.err.println("Node #"+myNode.getID()+":Destination IP("+nextHopDestIP+") not in my neigborhood. Please Re-route, are you sending to yourself");
                 System.err.println("Node #"+myNode.getID()+"has +"+myNode.neighboursList.size()+"neighbors");
                 return ERROR;
            }
            myNode.getNodeGUI().colorCode.mark(colorProfileGeneric, ColorProfileGeneric.TRANSMIT, 2);
            netEntity.send(ipMsg, Constants.NET_INTERFACE_DEFAULT, macAddress);
        }
        return SUCCESS; 
    }
    public NetAddress getThroughShortestPath(Location2D destLocation){
      double closestDist=myNode.getLocation2D().distanceTo(destLocation);
      NetAddress closestNode=myNode.getIP();
      LinkedList<NodeEntry>neighboursLinkedList=myNode.neighboursList.getAsLinkedList();
      
      for(NodeEntry nodeEntry:neighboursLinkedList){
          double neighbourDistance=nodeEntry.getNCS_Location2D()
                                            .fromNCS(myNode.getLocationContext())
                                            .distanceTo(destLocation);
          if(neighbourDistance<closestDist){
              closestDist=neighbourDistance;
              closestNode=nodeEntry.ip;
          }
      }
      return closestNode;
    }
    int jmlDrop=0;
//**USER CODE FUNCTIONS**//
    public void dropnotify(Message msg, MacAddress nextHopMac, Reason reason){
        if(reason==Reason.PACKET_SIZE_TOO_LARGE){
            System.out.println("WARNING: Packet Size too large-unable to transmit");
            throw new RuntimeException("Packet size too large-unable to transmit");
        }
        if(reason==Reason.NET_QUEUE_FULL){
            jmlDrop++;
            //System.out.println("Warning : NET Queue Full");
            //throw new RuntimeException("NET Queue full");
        }
        if(reason==Reason.UNDELIVERABLE||reason==Reason.MAC_BUSY)
            System.out.println("WARNING: Cannot relay packet to destination node"+nextHopMac);
        //wait 5 second and try again
        JistAPI.sleepBlock(500*Constants.MILLI_SECOND);
        this.send((NetMessage)msg);
    }
 /* **************************************** *
    * SWANS network's stack hook-up interfaces *
    * **************************************** */
    public RouteInterface getProxy()
    {
        return self;
    }
    
    public void setNetEntity(NetInterface netEntity)
    {
        if(!JistAPI.isEntity(netEntity))throw new IllegalArgumentException("excepted entity");
        if(this.netEntity!=null)throw new IllegalStateException("net entity already set");
        this.netEntity=netEntity;
    } 
    
    public void setAppInterface(AppInterface appInterface)
    {
        this.appInterface=appInterface;
    }
    
    public NetAddress SearchChIPAddress(){
        NetAddress ipNode=myNode.getIP();
        LinkedList<NodeEntry>neighboursLinkedList=myNode.clusterNeighboursList.getAsLinkedList();
        
        NetAddress nextNode=null;
        double distToCluster=10000;
        for(NodeEntry nodeEntry:neighboursLinkedList){
            if(nodeEntry.distToCluster<distToCluster){
                distToCluster=nodeEntry.distToCluster;
                nextNode=nodeEntry.ip;
                System.out.println("Next Node IP: "+nextNode.getIP());
            }
        }
        if(distToCluster<myNode.distToClusterCenter){
            this.myNode.ChAddress=nextNode;
            System.out.println("Next Node IP(Cluster Head IP Address + "+nextNode.getIP());
            return nextNode;
        }
        else
            return ipNode;
    }
    private NetAddress getChIP(){
        NetAddress nextHopAddress=null;
        LinkedList<NodeEntry>neighboursLinkedList=myNode.neighboursList.getAsLinkedList();
        
        double jarakToCluster=1000000;
        for(NodeEntry nodeEntry:neighboursLinkedList){
            if(nodeEntry.clusterId==myNode.clusterId){
                if(nodeEntry.distToCluster<jarakToCluster){
                    jarakToCluster=nodeEntry.distToCluster;
                    nextHopAddress=nodeEntry.ip;
                }
            }
        }
    System.out.println("Cluster Head yang terpilih dari node "+myNode.getIP().toString()+":"+nextHopAddress.getIP().toString());
    return nextHopAddress;
    }
    
    private void handleQueryMessage2(Location2D targetLocation,NetMessage msg){
        SGPWrapperMessage msgSGP=(SGPWrapperMessage)((NetMessage.Ip)msg).getPayload();
    //jika ini bukan merupakan node terakhir maka lanjutkan sebarkan
        if(this.myNode.ChAddress==null){
            this.myNode.ChAddress=this.SearchChIPAddress();
        }
        NetAddress CH_Address=this.myNode.ChAddress;
        
        if(CH_Address!=this.myNode.getIP()){
            //sending to CH
            System.out.println("Sending Query Message to Cluster head: "+CH_Address.toString());
            NetMessage.Ip copyOfMsg=new NetMessage.Ip(msgSGP, ((NetMessage.Ip)msg).getSrc(),
                                        ((NetMessage.Ip)msg).getDst(),
                                        ((NetMessage.Ip)msg).getProtocol(),
                                        ((NetMessage.Ip)msg).getPriority(),
                                        ((NetMessage.Ip)msg).getTTL(),
                                        ((NetMessage.Ip)msg).getId(),
                                        ((NetMessage.Ip)msg).getFragOffset());
            sendToLinkLayer(copyOfMsg, CH_Address);
        }
        else{
            NetAddress nextHopIP=getThroughShortestPath(targetLocation);
    //jika tidak ada node yang lebih dekat ketujuan dibandingkan dirinya sendiri maka node ini akan menerima pesan tersebut
            if(nextHopIP.hashCode()==myNode.getIP().hashCode())
                sendToAppLayer(msgSGP.getPayload(),null);
            else{
                NetMessage.Ip copyOfMsg=new NetMessage.Ip(msgSGP, ((NetMessage.Ip)msg).getSrc(),
                                        ((NetMessage.Ip)msg).getDst(),
                                        ((NetMessage.Ip)msg).getProtocol(),
                                        ((NetMessage.Ip)msg).getPriority(),
                                        ((NetMessage.Ip)msg).getTTL(),
                                        ((NetMessage.Ip)msg).getId(),
                                        ((NetMessage.Ip)msg).getFragOffset());
                sendToLinkLayer(copyOfMsg, nextHopIP);
            }
        }
    }
    private void handleMessageDataValue(Location2D targetLocation, NetMessage msg){
        SGPWrapperMessage msgAGW=(SGPWrapperMessage)((NetMessage.Ip)msg).getPayload();
        
        NetAddress ip=((NetMessage.Ip)msg).getDst();
        //jika node tersebut bukan sink node(destination)
        if(!(myNode.getIP()==ip)){
            NetMessage.Ip copyOfMsg=new NetMessage.Ip(msgAGW, ((NetMessage.Ip)msg).getSrc(),
                                      ((NetMessage.Ip)msg).getDst(),
                                      ((NetMessage.Ip)msg).getProtocol(),
                                      ((NetMessage.Ip)msg).getPriority(),
                                      ((NetMessage.Ip)msg).getTTL(),
                                      ((NetMessage.Ip)msg).getId(),
                                      ((NetMessage.Ip)msg).getFragOffset());
    //broadcast kesemua node tetangga
    //disini kita harus ngirim ke ch dengan tipe data NetAddress nextHop=getCH...
    //disini tempat kita mencari NetAddress dari ClusterHead
            NetAddress nextHop=this.getChIP();
            sendToLinkLayer(copyOfMsg, nextHop); //ini mengirim ke CH
            
            System.out.println("Node "+myNode.getID()+" Forwarding Sensing Message to cluster head");
        }
        else if(myNode.getIP()==this.getChIP()){
    //jika dia sendiri adalah CH, maka gunakan SGP
            NetMessage.Ip copyOfMsg=new NetMessage.Ip(msgAGW, ((NetMessage.Ip)msg).getSrc(),
                                      ((NetMessage.Ip)msg).getDst(),
                                      ((NetMessage.Ip)msg).getProtocol(),
                                      ((NetMessage.Ip)msg).getPriority(),
                                      ((NetMessage.Ip)msg).getTTL(),
                                      ((NetMessage.Ip)msg).getId(),
                                      ((NetMessage.Ip)msg).getFragOffset());
    //broadcast ini kesemua tetangga
    //disini harus mengirim kecH dengan tipe data NetAddress nextHop=getCH(...        
    //disni tempat mencari NetAddress dari CH
          NetAddress nextHop=this.getChIP();
          sendToLinkLayer(copyOfMsg, NetAddress.ANY);
          System.out.println("Node "+myNode.getID()+"Forwarding Sensing Message to Cluster Head");
        }
        else{ //Sink node
            sendToAppLayer(msgAGW.getPayload(), null);
        }
    }
    public void start(){
        //nothing
    }

    public void dropNotify(Message msg, MacAddress nextHopMac, Reason reason) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}

  

