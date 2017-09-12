/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package myapp;

import java.io.IOException;
import javax.servlet.http.*;
import java.util.*;
import java.io.*;
import javax.servlet.annotation.MultipartConfig;

/**
* This is a tic tac toe program for deployed on google cloud.
* http://tictactoe-179205.appspot.com/demo
* 
* This uses Jetty Web Server, which uses HttpServlet 3.1
* This handles POST requests from Slack.
* This requires 2 users to play tic tac toe.
* Many games can be played on many channels, but only one
* game can be played in one channel at a time.
*
* This uses a simple in memory key value map instead of a KV DB,
* which is used to keep track of game states for each channel.
* 
* All POSTs require the following parameters:
*   command:/ttt
*   user: username
*   channel: channelname
*   text: text command used to play tic tac toe
*
* Summary of commands for game play.
* 
*   start [username_to_invite]  // type start or start username. 
*                               // if start, anyone who types start can play.
*                               // if start username, only username can play.
*   put <row> <col>             // valid 0:2 for row, 0:2 for column
*   resign|quit                 // quit the game. The initiator loses.
*   status                      // print the board for anyone to see.
*   help                        // help command
*   restart                     // anyone can restart game if no action has
*                               // taken place within 2 minutes, used to free
*                               // up channel.
* 
* Example game play:
* u1: start
* u2: start
* u1: put 0 0
* u1: put 1 1 // results in error message
* u2: put 1 1
* u1: put 1 0
* u1: status  // shows current board state
* u3: status  // shows current board state
* u2: put 1 0 // results in error message
* u1: put 2 0 // results in error message
* u2: put 0 1
* u1: put 2 0 // game over, u1 wins
* u2: put 2 2 // results in error message
* u1: put 2 2 // results in error message
* u3: status  // shows u1 wins
* u2: start
* u1: start   // game starts over again
* u2: start   // results in error message
* wait 5 minutes
* u3: restart
* u3: start u1
* u2: start   // results in error message
* u1: start   // new game play with u3
*
* Summary of all classes.
* 
* DemoServlet		index_DEMOSERVLET
* TTTResponse		index_TTTRESPONSE
* Coord			index_COORD
* Memory		index_MEMORY
* Processor		index_PROCESSOR
* Command		index_COMMAND
* CMD			index_CMD
* TTT			index_TTT
* TTTRecord 		index_TTTRECORD
* TTTUserStats		index_TTTUSERSTATS
* TTTConfig             index_TTTCONFIG
*
* Functionality flow:
* 1. POSTs get handled in DemoServlet, which passes the parameter map
*    of request to Processor.
* 2. Processor processes the commands from user. The parameter map is
*    used to construct a Command, which is passed to Processor.
*    A new TTT board is created per channel in Memory. The board is TTT.
* 3. Commmand has user, channel, and TTT move command coordinates.
*    Coord is just a row,col set used for TTT moves.
* 4. TTT is the board, which has user1 and user2, and game state.
* 5. TTTResponse is the response to return to HttpServletResponse, which
*    is used to indicate command status and message. The response is
*    used to determine if it should be an error, private message, or
*    public message.
* 6. TTTRecord, TTTUserStats can be used to do historical records.
*    This is not implemented.
*
*/

/**
* index_DEMOSERVLET
* 
* 
* public void init
* public void destroy
* private String printHeaders(HttpServletRequest req)
* private String getParams(HttpServletRequest req)
* public void doGet(HttpServletRequest req, HttpServletResponse rsp)
* public void doPost(HttpServletRequest req, HttpServletResponse rsp)
* 
*/
@MultipartConfig
public class DemoServlet extends HttpServlet {
  private PrintWriter pw = null;
  private Processor processor = null;

  public void init() {
    p("Init called\n");
    String filename = "log.demo.log";
    processor = new Processor();
    try {
      //File file = new File(filename);
      //FileWriter fw = new FileWriter(file);
      //BufferedWriter bw = new BufferedWriter(fw);
      //pw = new PrintWriter(bw, true);
    } catch(Exception e) {
    }
  }

  public void destroy() {
    p("Destroy called\n");
    if(pw != null) {
      pw.close();
      pw = null;
    }
  }

  private void p(String f, Object ...o) {
    String msg = String.format(f, o);
    System.out.printf(msg);
    if(pw != null) {
      pw.print(msg);
    }
  }

  private String printHeaders(HttpServletRequest req) {
    Enumeration<String> headerNames = req.getHeaderNames();
    StringBuilder sb = new StringBuilder();
    while(headerNames.hasMoreElements()) {
      String headerName = headerNames.nextElement();
      Enumeration<String> headerValues = req.getHeaders(headerName);
      String msg = String.format("\theaderName:%s = ", headerName);
      if(TTTConfig.debug) {
          p(msg);
      }
      sb.append(msg);
      while(headerValues.hasMoreElements()) {
        String headerValue = headerValues.nextElement();
        msg = String.format("%s ", headerValue);
        if(TTTConfig.debug) {
          p(msg);
        }
        sb.append(msg);
      }
      if(TTTConfig.debug) {
        p("\n");
      }
      sb.append("\n");
    }
    return sb.toString();
  }

  private String getParams(HttpServletRequest req) {
    StringBuilder sb = new StringBuilder();
    p("\tgetParams ");
    sb.append("\tgetParams ");
    Map<String, String []> map = req.getParameterMap();
    HashMap<String, String> hmap = new HashMap<>();
    String msg = null;
    for(Map.Entry<String,String []> kv: map.entrySet()) {
      String key = kv.getKey();
      msg = String.format("%s", key);
      sb.append(msg);
      String [] sary = kv.getValue();
      String value = null;
      for(String s: sary) {
        msg = String.format("=%s", s);
        sb.append(msg);
        value = s;
      }
      hmap.put(key, value);
      msg = " ";
      sb.append(msg);
    }
    sb.append("\n");
    msg = sb.toString();
    if(TTTConfig.debug) {
      p(msg);
    }

    TTTResponse result = processor.process(hmap);
    String rspString = null;

    if(result == null) {
      if(TTTConfig.debug) {
          p("getParams response null\n");
      }
      String rspType = "\"response_type\": \"ephemeral\"";
      rspString = String.format("{ %s, \"text\": \"%s\" }", rspType, msg);
    }
    else {
      if(TTTConfig.debug) {
        p("getParams response OK\n");
      }
      msg = result.message;
      if(result.status == CMD.OK_PUBLIC) {
        String rspType = "\"response_type\": \"in_channel\"";
        rspString = String.format("{ %s, \"text\": \"%s\" }", rspType, msg);
      }
      else {
        String rspType = "\"response_type\": \"ephemeral\"";
        rspString = String.format("{ %s, \"text\": \"%s\" }", rspType, msg);
      }
    }
    
    return rspString;
  }

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse rsp)
      throws IOException {
      if(TTTConfig.debug) {
        p("doGet called\n");
      }
    printHeaders(req);
    rsp.setContentType("text/plain");
    rsp.getWriter().println("{ \"name\": \"World Whatever\" }");
  }

  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse rsp)
    throws IOException
  {
    p("doPost called\n");
    String msg = getParams(req);
    rsp.setContentType("application/json");
    rsp.getWriter().println(msg);
  }

}

/**
* index_TTT
* TTT
* summary of methods:
* TTT()
* public void reset()
* public boolean getIsReady()
* public void setIsReady()
* public String getBoardString()
* public boolean set(int row, int col)
* private evaluateBoard()
* public boolean setInvitee(String username) 
* public String getInvitee()
* public boolean setPlayer1(String username)
* public String getPlayer1()
* public boolean setPlayer2(String username)
* public String getPlayer2()
* public boolean getIsDone()
* public String getWinner()
* public void printBoard()
* public String getCurrentPlayer()
* public boolean isPlayer1()
* public boolean quit(String username)
* public long getTimeLastMS() 
* 
*/
class TTT {
  int size = 3;
  char [][] board = new char[size][size];
  boolean isPlayer1 = true;
  String player1 = null;
  String player2 = null;
  String playerInvitee = null;
  String winner = null;
  boolean isDone = false;
  boolean isReady = false;
  int numPopulated = 0;
  long timeLastMS = System.currentTimeMillis();

  public TTT() {
    reset();
  }

  public void reset() {
    for(int i = 0; i < size; i++) {
      for(int j = 0; j < size; j++) {
        board[i][j] = ' ';
      }
    }
    isDone = false;
    winner = null;
    player1 = null;
    player2 = null;
    playerInvitee = null;
    isPlayer1 = true;
    numPopulated = 0;
    isReady = false;
    timeLastMS = System.currentTimeMillis();
  }

  public long getTimeLastMS() {
    return timeLastMS;
  }

  public String getCurrentPlayer() {
    if(isPlayer1) {
      return player1;
    }
    return player2;
  }

  public boolean isPlayer1() {
    return isPlayer1;
  }

  public boolean setInvitee(String username) {
    playerInvitee = username;
    return true;
  }

  public String getInvitee() {
    return playerInvitee;
  }

  public boolean getIsReady() {
    return isReady;
  }

  public void setIsReady() {
    if(isDone) {
      isReady = false;
    }
    else if(player1 != null && player2 != null && winner == null) {
      isReady = true;
    }
    else {
      isReady = false;
    }
  }

  private void p(String f, Object ...o) {
    String msg = String.format(f, o);
    System.out.printf(msg);
  }

  public String getBoardString() {
    StringBuilder sb = new StringBuilder();
    String msg;
    msg = "\n" + "```";
    sb.append(msg);
    for(int i = 0; i < size; i++) {
      msg = String.format("+-+-+-+\n");
      sb.append(msg);
      msg = String.format("|%s|%s|%s|\n", board[i][0], board[i][1], board[i][2]);
      sb.append(msg);
    }
    msg = String.format("+-+-+-+```\n");
    sb.append(msg);
    msg = sb.toString();
    return msg;
  }

  public boolean set(String user, int row, int col) {
    if(getIsDone()) {
      return false;
    }
    if(!user.equals(getCurrentPlayer())) {
      return false;
    }
    timeLastMS = System.currentTimeMillis();
    if(row < 0 || row >= size || col < 0 || col >= size) {
      return false;
    }
    char c = board[row][col];
    if(c != ' ') {
      return false;
    }
    board[row][col] = (isPlayer1) ? 'X' : 'O';
    numPopulated++;
    evaluateBoard();
    isPlayer1 = !isPlayer1;
    return true;
  }

  public boolean quit(String user) {
    if(isDone) {
      return false;
    }
    if(!isReady) {
      reset();
      return false;
    }
    if(user.equals(player1)) {
      isDone = true;
      winner = player2;
    }
    else if(user.equals(player2)) {
      isDone = true;
      winner = player1;
    }
    else {
      return false;
    }
    return true;
  }

  private void evaluateBoard() {
    if(isDone) {
      return;
    }
    char c;
    // check across
    for(int i = 0; i < size; i++) {
      c = board[i][0];
      if(c != ' ' && c == board[i][1] && c == board[i][2]) {
        winner = (c == 'X') ? player1 : player2;
        isDone = true;
        return;
      }
    }
    // check vertical
    for(int i = 0; i < size; i++) {
      c = board[0][i];
      if(c != ' ' && c == board[1][i] && c == board[2][i]) {
        winner = (c == 'X') ? player1 : player2;
        isDone = true;
        return;
      }
    }
    // check diagonal
    c = board[0][0];
    if(c != ' ' && c == board[1][1] && c == board[2][2]) {
      winner = (c == 'X') ? player1 : player2;
      isDone = true;
      return;
    }
    c = board[2][0];
    if(c != ' ' && c == board[1][1] && c == board[0][2]) {
      winner = (c == 'X') ? player1 : player2;
      isDone = true;
      return;
    }
  }

  public boolean setPlayer1(String username) {
    if(player1 == null) {
      player1 = username;
      setIsReady();
      timeLastMS = System.currentTimeMillis();
      return true;
    }
    if(player1.equals(username)) {
      setIsReady();
      return true;
    }
    return false;
  }

  public String getPlayer1() {
    return player1;
  }
 
  public boolean setPlayer2(String username) {
    if(username.equals(player1)) {
      return false;
    }
    if(player2 == null) {
      if(playerInvitee != null && !playerInvitee.equals(username)) {
        return false;
      }
      // if invitee is null or invitee == player then pass
      // if invitee is not null and invitee != player then fail
      player2 = username;
      setIsReady();
      timeLastMS = System.currentTimeMillis();
      return true;
    }
    if(player2.equals(username)) {
      setIsReady();
      return true;
    }
    return false;
  }

  public String getPlayer2() {
    return player2;
  }

  public boolean getIsDone() {
    return isDone;
  }

  public String getWinner() {
    return winner;
  }

  public void printBoard() {
    String msg = getBoardString();
    p(msg);
  }
}

/**
* index_TTTRECORD
*/
class TTTRecord {
  public String winnerUser = null;
  public String loserUser = null;
  public String channel = null;
}

/**
* index_TTTUSERSTATS
*/
class TTTUserStats {
  public String user = null;
  public List<TTTRecord> history = new ArrayList<>();
  public int totalPlayed = 0;
  public int totalWin = 0;
  public int totalLoss = 0;
}

/**
* index_MEMORY
* Memory
* Summary of methods:
* public Memory() 
* public clearAll()
* public boolean updateBoard(String channel, TTT board)
* public boolean setBoard(String channel, TTT board)
* public boolean isBoardActive(String channel)
* public TTT getBoard(String channel)
* private boolean addHistory(String channel, TTT board)
* public List<TTT> getHistory(String channel)
*/
class Memory {
  Map<String, TTT> mapCurrent = new HashMap<>();
  Map<String, List<TTT>> mapHistory = new HashMap<>();
  Map<String, TTTUserStats> stats = new HashMap<>();

  public Memory() {
  }

  public void clearAll() {
    mapHistory.clear();
    mapCurrent.clear();
  }

  public void clearAllActive() {
    mapCurrent.clear();
  }

  public void clearBoard(String channel) {
    TTT ttt = mapCurrent.get(channel);
    if(ttt != null) {
      mapCurrent.put(channel, null);
    }
  }

  public void clearChannelAll(String channel) {
  }

  /** update state of board, must be same board as before. */
  public boolean updateBoard(String channel, TTT board) {
    TTT ttt = mapCurrent.get(channel);
    if(board != ttt) {
      return false;
    }
    return true;
  }

  /** Set a new board, previous board must be done first. */
  public boolean setBoard(String channel, TTT board) {
    if(isBoardActive(channel)) {
      return false;
    }
    TTT ttt = mapCurrent.get(channel);
    if(ttt != null) {
      addHistory(channel, ttt);
    }
    mapCurrent.put(channel, board);
    return true;
  }

  public boolean isBoardActive(String channel) {
    TTT ttt = mapCurrent.get(channel);
    if(ttt == null) {
      return false;
    }
    return (!ttt.getIsDone());
  }

  public TTT getBoard(String channel) {
    return mapCurrent.get(channel);
  }

  private boolean addHistory(String channel, TTT board) {
    if(board == null || (!board.getIsDone())) {
      return false;
    }
    List<TTT> list = mapHistory.get(channel);
    if(list == null) {
      list = new ArrayList<>();
      mapHistory.put(channel, list);
    }
    list.add(board);
    return true;
  }

  public List<TTT> getHistory(String channel) {
    return mapHistory.get(channel);
  }
}

/**
* index_COORD
*/
class Coord {
  public int row = -1;
  public int col = -1;
  public char val = ' ';
  public Coord(int row, int col) {
    this.row = row;
    this.col = col;
  }
  public Coord(int row, int col, char val) {
    this.row = row;
    this.col = col;
    this.val = val;
  }
}

/**
* index_CMD
*/
class CMD {
  public static final String NOP = "nop";
  public static final String START = "start";
  public static final String PLAY = "play";
  public static final String PUT = "put";
  public static final String RESTART = "restart";
  public static final String QUIT = "quit";
  public static final String RESIGN = "resign";
  public static final String STATUS = "status";
  public static final String HELP = "help";
  public static final int ERR = 0;
  public static final int OK = 1;
  public static final int OK_PUBLIC = 2;
}

/**
* index_COMMAND
* Command
* Summmary of methods:
* public Command(String username, String channel)
* public void setCommand(String command)
* public void setCommand(String command, Coord coord)
* public void setInvitee(String username)
* public String getInvitee()
* public String getUser()
* public String getCommand()
* public String getChannel()
* public Coord getCoord()
*/
class Command {
  String username = null;
  String usernameDst = null;
  String command = null;
  String channel = null;
  Coord coord = null;
  
  public Command(String username, String channel) {
    this.username = username;
    this.channel = channel;
  }

  public void setCommand(String command) {
    this.command = command;
  }
  public void setCommand(String command, Coord coord) {
    this.command = command;
    this.coord = coord;
  }
  public void setInvitee(String username) {
    usernameDst = username;
  }
  public String getInvitee() {
    return usernameDst;
  }
  public String getUser() {
    return username;
  }
  public String getCommand() {
    return command;
  }
  public String getChannel() {
    return channel;
  }
  public Coord getCoord() {
    return coord;
  }
}

/**
* index_TTTRESPONSE
*/
class TTTResponse {
  public int status = CMD.ERR;
  public String message = null;
  public TTTResponse() {
  }
  public TTTResponse(int status) {
    this.status = status;
  }
  public TTTResponse(int status, String message) {
    this.status = status;
    this.message = message;
  }
}

/**
* index_PROCESSOR
* Processor processes incoming commands onto TTT.
* 
* Rules: One game per channel. So have a map of channel_name and game state.
* 
* Commands:
* /ttt start @username
* /ttt start
* /ttt put row col
* /ttt restart
* /ttt quit
* /ttt resign offer
* /ttt resign accept
* /ttt status
* /ttt help
* 
* Summary of methods:
* public Processor()
* public StringProcess(Map<String,String> map)
* private Command createCommand(Map<String,String> map)
* private String executeCommand(Command command)
* private isValueNullOrZero(String s)
* private boolean validateKeys(Map<String,String> map)
* private String executeCommandStart(Command command)
* 
*/
class Processor {
  private Memory memory = null;

  public Processor() {
    memory = new Memory();
  }

  private void p(String f, Object ...o) {
    String msg = String.format(f, o);
    System.out.printf(msg);
  }

  public TTTResponse process(Map<String, String> map) {
    if(TTTConfig.debug) {
      p("process called\n");
    }
    if(!validateKeys(map)) {
      if(TTTConfig.debug) {
          p("Processor validateKeys bad\n");
      }
      return null;
    } 
    Command cmd = createCommand(map);
    if(cmd == null) {
      if(TTTConfig.debug) {
          p("Processor createCommand null\n");
      }
      return null;
    }
    return executeCommand(cmd);
  }

  private Command createCommand(Map<String, String> map) {
    if(TTTConfig.debug) {
      p("createCommand called\n");
    }
    String scmd = map.get("command");
    String suser = map.get("user_name");
    String schannel = map.get("channel_name");
    String stext = map.get("text"); 

    if(!"/ttt".equals(scmd)) {
      if(TTTConfig.debug) {
          p("scmd %s is not /ttt\n", scmd);
      }
      return null;
    }

    String [] sary = stext.split("\\s+");
    int szary = sary.length;

    if(szary == 0 || szary > 4) {
      return null;
    }

    Command command = new Command(suser, schannel);

    if     (CMD.START.equals(sary[0])) {
      if(szary > 2) {
        return null;
      }
      if(szary == 2) {
        command.setInvitee(sary[1]);
      }
      command.setCommand(CMD.START);
    }
    else if(CMD.PUT.equals(sary[0])) {
      if(szary != 3) {
        return null;
      }
      try {
        int row = Integer.parseInt(sary[1]);
        int col = Integer.parseInt(sary[2]);
        Coord coord = new Coord(row, col);
        command.setCommand(CMD.PUT, coord);
      } catch(Exception e) {
        return null;
      }
    }
    else if(CMD.RESTART.equals(sary[0])) {
      command.setCommand(CMD.RESTART);
    }
    else if(CMD.QUIT.equals(sary[0])) {
      command.setCommand(CMD.QUIT);
    }
    else if(CMD.RESIGN.equals(sary[0])) {
      command.setCommand(CMD.RESIGN);
    }
    else if(CMD.STATUS.equals(sary[0])) {
      command.setCommand(CMD.STATUS);
    }
    else if(CMD.HELP.equals(sary[0])) {
      command.setCommand(CMD.HELP);
    }
   
    if(command.getCommand() == null) {
      return null;
    }
    return command;
  }

  /**
   * executeCommandStart(Command command)
   * 
   * If board is done, then start a new board.
   *
   * A new board starts off with board not ready, since it 
   * needs 2 users.
   *
   * If new board is set but not ready, then 1 user has
   * started the board, but a second user has not yet joined.
   * 
   * If a not ready board is called start, then check: 
   * if user is either invitee or there is no invitee, then
   * user can join game, and board ready is set. If user
   * is not invitee, or there is already 2 players, then false.
   * 
   * Once board is ready, then print board and first user
   * goes first.
   * 
   */ 
  private TTTResponse executeCommandStart(Command command) {
    p("executeCommandStart\n");
    String channel = command.getChannel();
    String user = command.getUser();
    String invitee = command.getInvitee();
    StringBuilder sb = new StringBuilder();

    TTT ttt = memory.getBoard(channel);
   
    if(ttt == null || ttt.getIsDone()) {
      // is entirely clean slate or last game is done,
      // then this user can start a new one.
      ttt = new TTT();
      ttt.setPlayer1(user);
      if(invitee != null) {
         ttt.setInvitee(invitee);
      }
      memory.setBoard(channel, ttt);
      TTTResponse response = new TTTResponse(CMD.OK);
      String message = "New board created. Pending...";
      response.message = message;
      return response;
    }
    else if(!ttt.getIsReady()) {
      // if waiting for second user to join then set ttt to ready. 
      // If first user is calling this again, then false.

      TTTResponse response = new TTTResponse(CMD.ERR);
      String player1 = ttt.getPlayer1();
      String player2 = ttt.getPlayer2();
      String playerInvitee = ttt.getInvitee();
      if(user.equals(player1)) {
        response.message = "Board already created. Pending...";
        return response;
      }
      if(player2 == null && (playerInvitee == null || playerInvitee.equals(user))) {
        ttt.setPlayer2(user);
        response.status = CMD.OK_PUBLIC;
        response.message = ttt.getBoardString() + "\n" + 
          String.format("Board ready. %s starts...", player1);
        return response;
      }
      response.message = "Cannot create new board. Board is active...";
      return response; 
    }
    TTTResponse response = new TTTResponse(CMD.ERR);
    response.message = "Board not created. Already existing board...";
    return response;
  }

  /**
   * executeCommandPut(Command command)
   * 
   * user puts mark on tic tac toe board.
   * 
   * If it is not user's turn, then reject.
   * 
   * If the coordinate is out of bounds or already marked, reject.
   * 
   * If game has not started yet or is game over, then reject.
   *
   * Syntax is /ttt put row col
   * 
   */
  private TTTResponse executeCommandPut(Command command) {
    p("executeCommandPut\n");
    String channel = command.getChannel();
    TTT ttt = memory.getBoard(channel);
    TTTResponse response = new TTTResponse(CMD.OK_PUBLIC);
    if(ttt == null) {
      response.status = CMD.OK;
      response.message = "No board active...";
    }
    else if(ttt.getIsDone()) {
      response.status = CMD.OK;
      response.message = "Game is done. Start a new one...";
    }
    else if(!ttt.getIsReady()) {
      response.status = CMD.OK;
      response.message = "Waiting for a second player...";
    }
    else {
      String user = command.getUser();
      Coord coord = command.getCoord();
      if(coord == null) {
        response.status = CMD.OK;
        response.message = "No coordinates set for move...";
        return response;
      }
      if(ttt.set(user, coord.row, coord.col)) {
        StringBuilder sb = new StringBuilder();
        String msg = ttt.getBoardString();
        sb.append(msg);

        if(ttt.getIsDone()) {
          String winner = ttt.getWinner();
          if(winner != null) {
            msg = String.format("Game over. Winner is %s", winner);
            sb.append(msg);
          }
          else {
            msg = "Game over. Draw...";
            sb.append(msg);
          }
        }
        else {
          msg = String.format("Next move is for player %s",
            ttt.getCurrentPlayer());
          sb.append(msg);
        }
        response.message = sb.toString();
      }
      else {
        response.status = CMD.OK;
        response.message = String.format("Cannot place move on %d %d",
          coord.row, coord.col);
      }
    }
    return response;
  }

  private TTTResponse executeCommandQuitResign(Command command) {
    p("executeCommandQuitResign\n");
    String channel = command.getChannel();
    String user = command.getUser();
    TTTResponse response = new TTTResponse();
    TTT ttt = memory.getBoard(channel);
    if(ttt == null) {
      response.status = CMD.OK;
      response.message = "Board is null. Cannot quit...";
    }
    else {
      if(ttt.quit(user)) {
        response.status = CMD.OK_PUBLIC;
        StringBuilder sb = new StringBuilder();
        String msg = ttt.getBoardString();
        sb.append(msg + "\n");
        msg = String.format("%s quit. Winner is %s\n", user, ttt.getWinner());
        sb.append(msg);
        response.message = sb.toString();
      }
      else {
        response.status = CMD.OK;
        response.message = "Game is done. Cannot quit...";
      }
    }
    return response;
  }

  private TTTResponse executeCommandStatus(Command command) {
    p("executeCommandStatus\n");
    String channel = command.getChannel();
    TTT ttt = memory.getBoard(channel);
    TTTResponse response = new TTTResponse(CMD.OK_PUBLIC);
    if(ttt == null) {
      response.message = "No board active...";
    }
    else {
      StringBuilder sb = new StringBuilder();
      String msg = ttt.getBoardString();
      sb.append(msg);
      sb.append("\n");
      if(ttt.getWinner() != null) {
        msg = String.format("Game done. Winner is %s\n", ttt.getWinner());
        sb.append(msg);
      }
      else {
        msg = String.format("Game active. Waiting for player %s\n", 
          ttt.getCurrentPlayer());
        sb.append(msg);
      }
      response.message = sb.toString();
    }
    return response;
  }
 
  private TTTResponse executeCommandRestart(Command command) {
    String channel = command.getChannel();
    String user = command.getUser();
    TTT ttt = memory.getBoard(channel);
    TTTResponse response = new TTTResponse(CMD.OK);

    if(ttt == null || ttt.getIsDone()) {
      response.message = "Game over. No need to restart. Type start...";
    }
    else {
      long timeCurrMS = System.currentTimeMillis();
      long timeDiffMS = timeCurrMS - ttt.getTimeLastMS();
      if(timeDiffMS < TTTConfig.timeout) {
        long timeDiffS = (TTTConfig.timeout - timeDiffMS) / 1000;
        response.message = String.format("Cannot restart. Wait %d seconds", 
          timeDiffS);
      }
      else {
        ttt.reset();
        memory.clearBoard(channel);
        response.message = "Board reset. Type start...";
      }
    } 
    
    return response;
  }

  private TTTResponse executeCommandHelp(Command command) {
    p("executeCommandHelp\n");
    TTTResponse response = new TTTResponse(CMD.OK);
    String msg = 
      "```" + 
      "HELP:\n" +
      "    start [username to invite] // eg start or start user1\n" +
      "    put <row> <col>            // eg put 1 2 for your move\n" +
      "    resign|quit                // resign or quit\n" +
      "    status                     // prints the board state\n" +
      "    help                       // help\n" +
      "```";
    response.message = msg;
    return response;
  }

  private TTTResponse executeCommand(Command command) {
    String cmd = command.getCommand();

    p("executeCommand cmd %s\n", cmd);

    if     (CMD.START.equals(cmd)) {
      return executeCommandStart(command);
    }
    else if(CMD.PUT.equals(cmd)) {
      return executeCommandPut(command);
    }
    else if(CMD.RESTART.equals(cmd)) {
      return executeCommandRestart(command);
    }
    else if(CMD.QUIT.equals(cmd)) {
      return executeCommandQuitResign(command);
    }
    else if(CMD.STATUS.equals(cmd)) {
      return executeCommandStatus(command);
    }
    else if(CMD.RESIGN.equals(cmd)) {
      return executeCommandQuitResign(command);
    }
    else if(CMD.HELP.equals(cmd)) {
      return executeCommandHelp(command);
    }
    else {
      return executeCommandHelp(command);
    }

    return null;
  }

  private boolean isValueNullOrZero(String s) {
    if(s == null || s.length() == 0)
      return true;
    return false;
  }

  private boolean validateKeys(Map<String, String> map) {
    if( isValueNullOrZero(map.get("channel_name")) ||
        isValueNullOrZero(map.get("channel_id")) ||
        isValueNullOrZero(map.get("user_name")) ||
        isValueNullOrZero(map.get("command")) ||
        isValueNullOrZero(map.get("user_id")) ||
        isValueNullOrZero(map.get("text")))
    {
      return false;
    }
    return true;
  }
  
}

/**
* index_TTTCONFIG
*/
class TTTConfig {
  public static final boolean debug = true;
  public static final int timeout = 1000 * 60 * 2;
}
