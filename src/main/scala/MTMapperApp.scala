package main.scala

import java.io.{File,FileInputStream,FileNotFoundException}
import java.io.PrintWriter
import java.util.Date
import java.util.concurrent.TimeUnit
import scala.io.{BufferedSource, Source}
import scala.util.{Try,Failure, Success}




object MTMapperApp extends App with MTMapperSheet{  
    System.setProperty("log.dir",args(3));
    private val startDate=new Date()
    
    logger.debug("ARGUMENT[INPUT FILE][{}]",args(0))
    logger.debug("ARGUMENT[OUTPUT FOLDER][{}]",args(1))
    logger.debug("ARGUMENT[MESSAGES FILE][{}]",args(2))
    logger.debug("ARGUMENT[LOGS FOLDER][{}]",args(3))
    logger.debug("ARGUMENT[YEAR][{}]",args(4))
    
    private val inputFile = args(0)
    private val outputDir = args(1) + File.separator
    private val configFile=args(2)
    private val year=args(4)
    

                            

	  private def getDateDiff(date1:Date,date2:Date,timeUnit:TimeUnit)= {
	      val diffInMillies = date2.getTime() - date1.getTime()
	      timeUnit.convert(diffInMillies,TimeUnit.MILLISECONDS)
	  }    
    
    
    private def getFileExtension(file:File)={
      val name = file.getName();
      name.substring(name.lastIndexOf(".") + 1);
    }
       
   
    private def isFileValid(file:File)=Array("xls","xlsx").contains(getFileExtension(file).toLowerCase())
      
        
    private def logError(filename:String,errType:String,errMsg:String)={
      logger.error("[{}][{}][{}]",filename,errType,errMsg)
    }
    
   
    val file=new File(inputFile)
    var input =None:Option[FileInputStream]
    var config=None:Option[BufferedSource]
    var exitStatus=0
    try{
      
      if(file.exists && file.length*2 >=file.getUsableSpace){
        val errMsg="FREE SPACE(%d)".format(file.getUsableSpace)
        throw  MTMapperException(Error.INSUFFICIENT_DISK_SPACE,errMsg)
      }  
      
      logger.info("[{}][FILE SIZE][{}]",file.getName,file.length.toString)
      logger.info("[{}][FREE SPACE][{}]",file.getName,file.getUsableSpace.toString)      
      
      
      if(!isFileValid(file)){
        val errMsg="FILE(%s)".format(file.getName)
        throw MTMapperException(Error.INVALID_FILE,errMsg)      
      }   
                
      
      input = Some(new FileInputStream(file))
      config= Some(Source.fromFile(configFile)("UTF-8"))
    
      val reject = (i: String) => !i.trim.isEmpty && !i.startsWith("#")
      val  mtScopes = config.get.getLines.filter {reject}.map(x=>x.trim.toUpperCase).toArray
      
      def isMessageInScope(messageType: String) = mtScopes.contains(messageType.toUpperCase)   
      
      getPSVListFromExcel(input.get,year) match {
         case Success(psvList)=>{
            var totalSize=0L
            psvList.filter{
              i=>{
                val inScope=isMessageInScope(i._1)
                logger.debug("SCOPE[{}][{}]",i._1,inScope.toString.toUpperCase)
                inScope
              }
            }.foreach(i=>{
              if(i._2.length>0){
                val outputFile=new File(outputDir+i._1.toLowerCase+"_"+year+".psv")
                val writer = new PrintWriter(outputFile, "UTF-8");
                i._2.foreach {writer.println}
                writer.flush
                writer.close
                totalSize+=outputFile.length;
                logger.debug("YEAR[{}][{}][EXISTS]",year.toString,i._1)
                logger.debug("FILE[{}][CLOSED]",outputFile.getName)
                logger.info("[{}][ROWS LENGTH][{}]",outputFile.getName,i._2.length.toString)
                logger.info("[{}][FILE SIZE][{}]",outputFile.getName,outputFile.length.toString)                
              }else{
                logger.debug("YEAR[{}][{}][NOT FOUND]",year.toString,i._1)
              }
            })
            logger.info("[{}][OUTPUT FILE SIZE][{}]",file.getName,totalSize.toString)
         }
         case Failure(ex) => throw ex
       }
      
    }catch {
      case ex:MTMapperException=> {        
        logError(file.getName,ex.errorType.toString,ex.errorMsg) 
        exitStatus=1
      }
      case ex:Exception => {
       logError(file.getName,ex.getClass.toString,ex.getMessage) 
       println(ex.getStackTraceString)
       exitStatus=1
      }
    }finally{//close all open files in case of exception 
      if(input.isDefined)input.get.close
      if(config.isDefined)config.get.close
      
      logger.debug("FILE[{}][CLOSED]",file.getName)
      logger.debug("EXIT[STATUS][{}]",exitStatus.toString)
      
			val finishDate=new Date();
			val units=TimeUnit.MILLISECONDS;   
			val duration=getDateDiff(startDate,finishDate,units);		
			logger.info("[{}][TOTAL DURATION(ms)][{}]",file.getName,duration.toString);			
      System.exit(exitStatus); 
    }
}