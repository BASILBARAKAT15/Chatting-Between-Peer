1. Part 1 (Question1)  
In this part, you are required to write Peer-to-peer Application Chatting Java UDP Socket 
Programming: Write a GUI application by using Java. Your program should have Text boxes, 
Buttons, Text Areas, Drop Downs etc. You should enter Source and destination IP addresses, 
port numbers. Also, you should display sent and received messages. See the sample for GUI 
Interface on Page 3. Two clients Are shown. The Buttons for Login, Logout, and the List of 
Online users is Not Required for This part. They are Required in Par2. Blocking Code is Not 
Accepted. 
Page 2 Shows Two Clients and sample GUI 
Extra requirements:  
● Add the timestamp for each exchanged message (for both sent and 
received) 
● Use Yellow color for sent messages 
● Use Orange color for received messages  
● Add button to delete selected message or delete all conversation from both 
sides. 
● Add a button called “Archive”, that will show a tab/window for archived 
messages. 
● Deleted messages will be moved to archive, later, after 2 minutes, they will 
be automatically removed. 
● Each archived message can be recovered/ restored if selected by the user 
to be restored. 
● Don’t Do: Replicate the same task by using TCP Socket instead of UDP 
Socket. 
● Create a log file that records all activities done on the program. 
2  
Socket API Project  
CE Networks-1 
Q2: [9 points]                                                                                                                 
2. Part 2 (Question2)  
In this part you are required to apply TCP Java Socket programming, but we will add Two Parts: 
A TCP Client to the Application described in 1. This Client requires the additions of buttons to 
register to the TCP Server Described in 2.2. The Server will simply keep a list of UDP clients 
involved in the Chatting. The TCP Server will send a message to each TCP client in the Active 
Chatting Client described in 1 to inform it of the List of Active Clients. This is similar to Skype 
and other chatting Servers. The server just keeps a list of those that Active Chatting Clients. So, 
you will need to modify the code in 1 to accommodate this Requirement. 
The Actual chatting will remain peer-to-peer. But the User chooses which Client to talk to from 
the List Provided by the Server. The login in the Clients is used for Registration to the TCP 
server to keep track of online Clients. You Should display the List of Active Client. See pages 5. 
Add a TCP Server that keeps track of the Active Chatting Clients as described in 2.1. It should 
Show a GUI with the Active List of Clients. The login in the Clients is used for Registration. See 
pages 6, 7. 
Blocking Code is Not Accepted 
Part1(HW1): This Figure Shows Two Clients used in Part 1 and 2. The Login/Logout and TCP 
Server are not required in Part1 but will be Required in Part2. 
Extra requirements:  
● Enable sent to all option 
● Add the name for each online user next to its corresponding IP and port 
number i.e. Ali 192.168.1.25 600 
● Add file at the TCP server side, which contains the credential for users, login 
information as user name and password as follows: 
User Name 
Password 
Ali 
1234 
Saly 
A20B 
Aws 
ABcd 
3  
Socket API Project  
CE Networks-1 
1Cb2 
Adam 
Both user name and password must be valid for login process, if OK, then the 
client can proceed and a notification can appear as (Logged in successful), else, a 
message will appear (invalid login information, either user name or password) 
● enable the logout button. 
● Use a different test color for each user. 
● Consider the username and passwords are not case sensitive. i.e. Ali = ali = 
aLi = …  
● After login in , the application displays on the GUI the last time you have 
successfully logged on.. 
● Add an option on the server side to add new users, and their corresponding 
passwords.  
o you have to add GUI at server side to enable adding new users 
● Notice, it is not allowed to add similar usernames.  
● Add option at each peer, so the peer can set its status as: 
o Active 
o Busy 
o Away 
● The other peers can know the status of other peers. 
● Add timer at each peer, if the peer not active for than 30 seconds, the 
status will be changed automatically to: away 
o Any event on keyboard, mouse will change the status from away to 
active 
4  
Socket API Project  
CE Networks-1 
Part 2(HW2): Here we show The TCP Server and # Chatting Clients each has a UDP and a TCP 
Client in the Code. Same Code for the Clients 
5  
Socket API Project  
CE Networks-1 
Client1 
Client 2: 
6  
Socket API Project  
CE Networks-1 
Client 3 
7  
Socket API Project  
CE Networks-1 
Extra Requirements for task#2: 
● Implement chat history export functionality (e.g., to a text file). 
● Display last successful login time after login. 
● Display Time Elapsed after login. 
● Add an extra task to send a file from one peer to another,  
o explore the file location on the local storage at the sender side 
o set  (C:\) as a default receiving/storing location at the receiving peer 
▪ could be changed by the receiving node. 
o show statistics on both peers (Sender and Receiver) 
▪ the total number of packets being sent/received 
▪ the file size 
▪ ….etc. 
o add a statistics for the E2E delay to get/download the file. 
o calculate delay jitter 
● Maintain a comprehensive log file to record: 
o Login and logout events with timestamps. 
o Message send/receive events with message content. 
o Message deletion and archival actions. 
o Status updates (active, busy, away) with timestamped transitions. 
o Store logs in a human-readable format with timestamps. 
● the code MUST be done and executed on different hosts 
o DON’T use single Host 
8  
Socket API Project  
CE Networks-1 
● make a detailed , well structured report, and upload it on time. 
● make a short video of max. 3 minutes stating the working functionality of 
all requirements. 
