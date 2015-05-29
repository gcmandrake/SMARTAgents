package plcClient;

import java.util.*;

public class Message {

   public static final int WRITE = 0;
   public static final int READ = 1; 
   public static final int BOOL = 2; 
   public static final int SHORT = 3; 
   public static final int STRING = 4; 
   public static final int INT = 5; 
   public static final int FLOAT = 6; 
   public static final String GLOBAL = "global"; 
   public static final String LOCAL = "local"; 
   public static final String SPACE = " "; 

   public static final int SUCCESS = 10; 
   public static final int ERROR = 11; 

   private int type, replyResult, operation;
   private String scope, var, value, replyValue;



   public Message(String result) {

      StringTokenizer tokens = new StringTokenizer(result, "\0\n ");
      String token, replyRes; 

      try {
         replyValue = tokens.nextToken();
         replyRes = tokens.nextToken();

	 if(replyRes.toUpperCase().contains("ERROR")) {
	    replyResult = ERROR;   
	 }
	 else {
	    replyResult = SUCCESS;   
	 }
      }
      catch(Exception e) {
	 //System.out.println("String didn't have the right number of tokens");
	 //e.printStackTrace();
         //return null;
      }
   }


   public Message(int operation, int type, String value, String scope, String var) {

      this.operation = operation;
      this.type = type;
      this.value = value;
      this.scope = scope;
      this.var = var;
   } 


   public Message(int operation, int type, String scope, String var) {
      this.operation = operation;
      this.type = type;
      this.value = null;
      this.scope = scope;
      this.var = var;
   }


   public String toString() {

      if(value == null) 
         return operation + SPACE + type + SPACE + scope + SPACE + var;
      else
         return operation + SPACE + type + SPACE + value + SPACE + scope + SPACE + var;
   }

   
   public int getResult() {
      return replyResult;
   }


   public String getValue() {
      return replyValue;
   }
}