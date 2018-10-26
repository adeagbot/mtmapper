package main.scala

import java.io.{File,FileInputStream,FileNotFoundException}
import scala.util.{Try,Failure, Success}

import org.apache.poi.ss.usermodel.{Workbook,Sheet,Cell,Row}
import org.apache.poi.ss.usermodel.WorkbookFactory

import com.typesafe.scalalogging.slf4j.LazyLogging
//import com.typesafe.scalalogging.LazyLogging

/**
* @author Terry Adeagbo
* @note The MT Mapper Sheet Utility
* @version 1.0
*/

trait MTMapperSheet extends LazyLogging{
  
   /**
   * @note Enums for indicating the Error type  
   */    
    protected object Error extends Enumeration {
      type Error =Value
      val INVALID_SHEET,
          INVALID_CELL,
          INVALID_COLUMNNAME,
          INVALID_COLUMNLENGTH,
          INVALID_ROW,
          INVALID_WORKBOOK,
          INVALID_FILE,
          INVALID_YEAR,
          INSUFFICIENT_DISK_SPACE= Value
    }  
  
 
  
  private val headers=Array("block", "block_order","field", "field_order", "status", 
                         "multiline_field","repeated_field","envelope_field",
                        "sequence", "sequence_order", "sequence_start", "sequence_end",
                        "column_name", "column_condition","column_offset", "column_order",
                        "subline_start", "subline_end", "subline_order",
                        "start_position","end_position")   
  
  private def removeWhiteSpaces(value:String)={
    //three non-breaking horizontal whitespace \u00A0, \u2007 and, \u202F
    value.replace("\u00A0"," ").replace("\u2007"," ").replace("\u202F"," ").trim
  }
  
  private def isYearValid(year:String)=getIntValue(year)>=1990
      

  private def getIntValue(text:String)={
    try{
      text.toInt
    }catch {
      case ex:Exception =>0
    }      
  }  
  
  private def getStringValue(value:String):String= removeWhiteSpaces(value).replaceAll("\\..+$","")

  private def getBooleanField(value:String)= value.toUpperCase.trim match {
    case "YES"|"Y" => "Y"
    case "NO"|"N" => "N"
    case _ => ""  
  }  
  
  private def getMappingRow(year:String,message_type:String,
                        block:String,block_order:String,field:String,field_order:String,
                        status:String,multiline_field:String,repeated_field:String,
                        envelope_field:String,
                        sequence:String,sequence_order:String,sequence_start:String,sequence_end:String,
                        column_name:String,column_condition:String,column_offset:String,column_order:String,
                        subline_start:String,subline_end:String,subline_order:String,
                        start_position:String,end_position:String):String={
      val status_code=status.toUpperCase.trim match {
        case "MANDATORY"|"M" => "M"
        case "OPTIONAL"|"O" =>"O"
        case _ => ""  
      }
      
      val condition_code=column_condition.toUpperCase.trim match {
        case "INCOMING"|"I" => "I"
        case "OUTGOING"|"O" => "O"
        case "ACCOUNT"|"A" => "A"  
        case "CODE"|"C" => "C"  
        case "PATTERN"|"P" => "P" 
        case "SYMBOL"|"S" => "S"
        case "TAG"|"T" => "T"  
        case "NUMERIC"|"N" => "N"    
        case  _ => column_condition.toUpperCase.trim  
      }
      val groupKey=year+"|"+message_type+"|"+
         block+"|"+block_order+"|"+field+"|"+field_order+"|"+status_code+"|"+
         getBooleanField(multiline_field)+"|"+
         getBooleanField(repeated_field)+"|"+
         getBooleanField(envelope_field)+"|"+
         sequence+ "|"+ sequence_order   
         
      val mapping=sequence_start+"|"+sequence_end+"|"+column_name+"|"+
                    condition_code+"|"+column_offset +"|"+column_order+"|"+
                    subline_start+"|"+subline_end+"|"+subline_order+"|"+
                    start_position+"|"+end_position       
      
      groupKey+"|"+mapping
    }
 
    case class MTMapperException (errorType:Error.Value,errorMsg:String) extends Exception(errorMsg) 
 
    @throws(classOf[MTMapperException])
    def getColumnValue(sheet:String,rowNum:Int,colNum:Int,cell:Cell):String={
      if(cell==null){
        val errMsg="SHEET(%s) ROW INDEX(%d) COLUMN INDEX(%d) CELL(NULL)".format(sheet,rowNum,(colNum+1))
        throw MTMapperException(Error.INVALID_CELL,errMsg)              
      }
      getStringValue(cell.toString);
    }       
    
    @throws(classOf[MTMapperException]) 
    def getRowValues(sheet:String,rowNum:Int,row:Row):IndexedSeq[String] ={
      if(row==null) {
        val errMsg="SHEET(%s) ROW INDEX(%d) ROW(NULL)".format(sheet,rowNum)
        throw MTMapperException(Error.INVALID_ROW,errMsg)           
      }
      val firstCol=row.getFirstCellNum.toInt      
      val lastCol=row.getLastCellNum.toInt 
      
      if(headers.length!=lastCol) {
        val errMsg="SHEET(%s) ROW(%d) LENGTH(%d)".format(sheet,rowNum,lastCol)
        throw MTMapperException(Error.INVALID_COLUMNLENGTH,errMsg)
      }
      
      for(i<-firstCol to lastCol-1)yield {
         getColumnValue(sheet,rowNum,i,row.getCell(i)) 
      }              
    }    
    
    @throws(classOf[MTMapperException]) 
    def getColumnHeaders(sheet:Sheet)={

      val rowIndex=1                  
      val firstRow=sheet.getFirstRowNum
      val sheetName=sheet.getSheetName
      val row=sheet.getRow(firstRow)
      val rowValues=getRowValues(sheetName,rowIndex,row)

      for ((colValue,colIndex)<- rowValues.toArray.zipWithIndex)yield {        
          val colName=getColumnValue(sheetName,rowIndex,colIndex,row.getCell(colIndex)) 
          if(!headers(colIndex).equalsIgnoreCase(colName)){
            val errMsg="SHEET(%s) ROW INDEX(%d) COLUMN INDEX(%d) COLUMN NAME(%s)".format(sheetName,rowIndex,(colIndex+1),colName)  
            throw MTMapperException(Error.INVALID_COLUMNNAME,errMsg) 
          } 
          colName
      }          
    }
    
    @throws(classOf[MTMapperException]) 
    def getSheetRows(message_type:String,sheet:Sheet): IndexedSeq[(String,String,String,String,String,
                                              String,String,String,String,
                                              String,String,String,String,
                                              String,String,String,String,
                                              String,String,String,String,String)] ={
      
      val firstRow=sheet.getFirstRowNum
      val lastRow=sheet.getLastRowNum
      val sheetName=sheet.getSheetName
      logger.debug("ROWLENGTH[{}][{}]",sheetName,lastRow.toString)
      for(i<-firstRow+1 to lastRow) yield {
        val rows =getRowValues(sheetName,i,sheet.getRow(i)) 
        rows  match {
          case IndexedSeq(a,b,c,d,
                          e,f,g,h,
                          i,j,k,l,
                          m,n,o,p,
                          q,r,s,t,u)=> (message_type,a,b,c,d,
                                      e,f,g,h,
                                      i,j,k,l,
                                      m,n,o,p,
                                      q,r,s,t,u)
          case _=> {
            val errMsg="SHEET(%s) ROW INDEX(%d) ROW(%s)".format(sheetName,i,rows.toString)
            throw MTMapperException(Error.INVALID_ROW,errMsg)   
          }
        }
      }
    }
    
     
   @throws(classOf[MTMapperException]) 
    def getPSVListFromExcel(file:FileInputStream,yearFilter:String):Try[IndexedSeq[(String,List[String])]]={
        Try({
          val workbook = WorkbookFactory.create(file)  
      
          //Retrieving the number of sheets in the Excel and retrieving the names
          val worksheets=for(i<-0 until workbook.getNumberOfSheets) yield{
            workbook.getSheetAt(i)
          }       
                    
          val common=worksheets.find(_.getSheetName.trim.toUpperCase=="COMMON")          
          if(common==None){
            throw MTMapperException(Error.INVALID_WORKBOOK,"SHEET(COMMON NOT FOUND)")          
          }
          
          if(!isYearValid(yearFilter)){
            val errMsg="YEAR(%s)".format(yearFilter)
            throw MTMapperException(Error.INVALID_YEAR,errMsg)      
          }              
          
          worksheets.foreach(x=>getColumnHeaders(x))          
                
          worksheets.filter{
           _.getSheetName.trim.toUpperCase!="COMMON"
          }.map(x=>{
            val msg_type=x.getSheetName.trim.toUpperCase
            val commonRows=getSheetRows(msg_type,common.get)
            val messageRows=getSheetRows(msg_type,x)        
            (msg_type,commonRows++messageRows)
          }).map(i=>{
            val mappingPSV=i._2.sortBy{i=>(getIntValue(i._3),getIntValue(i._5),
                getIntValue(i._11),getIntValue(i._17),getIntValue(i._20))
            }
            .map{i=>getMappingRow(year=yearFilter.trim,message_type=i._1,
                                      block=i._2,block_order=i._3,
                                      field=i._4,field_order=i._5,
                                      status=i._6,multiline_field=i._7,
                                      repeated_field=i._8,envelope_field=i._9,
                                      sequence=i._10,sequence_order=i._11,
                                      sequence_start=i._12,sequence_end=i._13,
                                      column_name=i._14,column_condition=i._15,
                                      column_offset=i._16,column_order=i._17,
                                      subline_start=i._18,subline_end=i._19,
                                      subline_order=i._20,start_position=i._21,end_position=i._22)                                
            }
            (i._1,mappingPSV.toList)
          })
       }) 
    }
   
 
}