package main.java;
public class Runner {
	  public static void main(String args[]){
		 if(args.length!=5){
			    System.err.println("Incorrect number of arguments passed: "+args.length);
			    System.out.println("Arguments expected: java -jar mtmapper.jar"
			    		+ " [input file] "
			    		+ " [output directory] "
			    		+ " [scope message scope file] "
			    		+ " [log directory] "
			    		+ " [year] ");
			    System.exit(1);     
		 }			  
		 System.setProperty("log.dir",args[3]);
		 main.scala.MTMapperApp.main(args);
	  }
}
