package com.sampath.sagt.eventaction.handler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
//import java.util.Properties;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import com.filenet.api.collection.ContentElementList;
import com.filenet.api.collection.RepositoryRowSet;
import com.filenet.api.constants.AutoUniqueName;
import com.filenet.api.constants.Cardinality;
import com.filenet.api.constants.DefineSecurityParentage;
import com.filenet.api.constants.PropertyState;
import com.filenet.api.constants.RefreshMode;
import com.filenet.api.core.ContentTransfer;
import com.filenet.api.core.Document;
import com.filenet.api.core.DynamicReferentialContainmentRelationship;
import com.filenet.api.core.Factory;
import com.filenet.api.core.Folder;
import com.filenet.api.core.ObjectStore;
import com.filenet.api.engine.EventActionHandler;
import com.filenet.api.events.ObjectChangeEvent;
import com.filenet.api.exception.EngineRuntimeException;
import com.filenet.api.exception.ExceptionCode;
import com.filenet.api.property.Properties;
import com.filenet.api.property.Property;
import com.filenet.api.query.SearchSQL;
import com.filenet.api.query.SearchScope;
import com.filenet.api.util.Id;
import com.filenet.apiimpl.query.RepositoryRowImpl;
import com.filenet.engine.metadatacache.ClassInfo;
import com.filenet.engine.metadatacache.ObjectStoreCache;
import com.filenet.engine.metadatacache.PropertyInfo;
import com.sampath.sagt.eventaction.util.FileNetConstants;
import com.sampath.sagt.eventaction.util.PropertyLoader;

/**
 * 
 * @author Mahesh G
 *
 */
public class AddDocumentEventHandler implements EventActionHandler {


	public static Logger log = Logger.getLogger(AddDocumentEventHandler.class);
	private static Calendar lastLog4jPropertiesReloadedOn = null;
	public static String log4jpath;
	String docName;
	String docReferenceNumber;
	String sourceSystem;
	String docDescription;
	String docObjectStoreName;
	String docVersionSeriesId;
	public static java.util.Properties prop;
	private static String[] OBJECT_ID = { "Id" };
	 
	static void init()
	{
		try{
			PropertyLoader property=new PropertyLoader();
			prop=property.loadPropertyFile();
			log4jpath=PropertyLoader.getProperty(prop, "LOGPATH");
			System.out.println("log4jpath : " + log4jpath);
			File fin = new File(log4jpath);
			Calendar lastModCal = Calendar.getInstance();
			lastModCal.setTimeInMillis(fin.lastModified());
			if(lastLog4jPropertiesReloadedOn != null)
			{
				log.debug((new StringBuilder("Log4j property file last loaded on:[")).append(lastLog4jPropertiesReloadedOn.getTime()).append("] ").append("Log4j property file last modified on:[").append(lastModCal.getTime()).append("]").toString());
			}
			if(lastLog4jPropertiesReloadedOn == null || lastLog4jPropertiesReloadedOn.before(lastModCal))
			{
				DOMConfigurator.configure(log4jpath);
				lastLog4jPropertiesReloadedOn = lastModCal;
				log.debug("Reloaded the Log4j property file as it has been modified since its last loaded time");
			}
		}catch (Exception e) {
			log.error("LC CaseEvenetHandler : init() Failed due to "+e.fillInStackTrace());
			System.out.println("Unable to  load logger in AddDocumentEventHandler ");
		}

	}


	@Override
	public void onEvent(ObjectChangeEvent event, Id arg1)
			throws EngineRuntimeException {
		try {
			//System.out.println("Entered AddDocumentEventHandler onEvent()");
			init();
			log.debug("LC CaseEvenetHandler : onEvent() begin ");
			ObjectStore os =  event.getObjectStore();

			String nicNo = null;
			String fileRefNum = null;
			Folder folder= null;

			Document documentRef = Factory.Document.fetchInstance(os, event.get_SourceObjectId(), null);	  
			fileRefNum=documentRef.getProperties().getStringValue(FileNetConstants.REFERENCE_NO);
			Document form = Factory.Document.fetchInstance(os, event.get_SourceObjectId(), null);
			
			String caseType = null ;
			boolean isAmendment=false;
			log.info("fileRefNum --"+fileRefNum );
			
			if(fileRefNum.indexOf("NR")>=0)
			{
				log.info("Inside NR");
				caseType =FileNetConstants.SAGT_NEW_REQ;
			}
			else if(fileRefNum.indexOf("PR")>=0)
			{
				log.info("Inside PR");
				caseType =FileNetConstants.SAGT_PROBATION;
			}
			else if(fileRefNum.indexOf("VS")>=0)
			{
				log.info("Inside VS");
				caseType =FileNetConstants.SAGT_VMOVISIT;
			}
			
			
			log.info("Final Case TYpe is -"+caseType+" isAmendment Val -"+isAmendment);
			SearchSQL sql = new SearchSQL();
			if(fileRefNum!=null && !fileRefNum.isEmpty()){
				log.debug("LC CaseEvenetHandler : onEvent()  FileRefNumber -"+fileRefNum);
				sql.setSelectList("PathName");
				sql.setFromClauseInitialValue(caseType, null, false); 
				sql.setWhereClause(FileNetConstants.REFERENCE_NO + "='"+fileRefNum+"'");

			}
			SearchScope search = new SearchScope(os);
			log.debug("SAGT CaseEvenetHandler : onEvent() Query-- "+sql);
			String lcCaseFolder=null;
			RepositoryRowSet myRows = search.fetchRows(sql, null, null, null);
			log.debug("SAGT CaseEvenetHandler : onEvent() Cases not Found "+ myRows.isEmpty());
			if(myRows!=null){
				RepositoryRowImpl rowImpl = (RepositoryRowImpl) myRows.iterator().next();
				lcCaseFolder=rowImpl.getProperties().getStringValue("PathName");
				log.debug("SAGT CaseEvenetHandler : onEvent() Cases Folder path "+ lcCaseFolder);
			}
			
				if(isAmendment&&lcCaseFolder!=null)
				   {
					    folder = Factory.Folder.fetchInstance(os, lcCaseFolder.toString(), null);
					    //Added by Akshay
					    copyDocumentProperties(os, folder, form, fileRefNum);
					    folder.setUpdateSequenceNumber(null);
						folder.save(RefreshMode.REFRESH);
					    DynamicReferentialContainmentRelationship rcr = Factory.DynamicReferentialContainmentRelationship.createInstance(os, null, 
				                           AutoUniqueName.AUTO_UNIQUE, 
				                           DefineSecurityParentage.DEFINE_SECURITY_PARENTAGE);
					    rcr.set_Tail(folder);
					    rcr.set_Head(event.get_SourceObject());
					    rcr.save(RefreshMode.REFRESH);
					    log.debug("BOCAttachmentEventhandler : onEvent() Document Filed in successfully in Folder "+lcCaseFolder);
			      }
			

			else if(lcCaseFolder!=null){
				folder = Factory.Folder.fetchInstance(os, lcCaseFolder.toString(), null);
				copyDocumentProperties(os, folder, form, fileRefNum);
				folder.setUpdateSequenceNumber(null);
				folder.save(RefreshMode.REFRESH);
				
				DynamicReferentialContainmentRelationship drcr = 
						Factory.DynamicReferentialContainmentRelationship.createInstance(os, null, 
								AutoUniqueName.AUTO_UNIQUE, 
								DefineSecurityParentage.DEFINE_SECURITY_PARENTAGE);
				drcr.set_Tail(folder);
				drcr.set_Head(event.get_SourceObject());
				drcr.save(RefreshMode.REFRESH);

				log.debug("LC CaseEvenetHandler : onEvent() Document Filed in successfully in Folder "+lcCaseFolder);
			}
		}
		catch (EngineRuntimeException e)
		{
			log.error("LC CaseEvenetHandler : onEvent() Failed due to "+e.getMessage());
			log.error("LC CaseEvenetHandler : onEvent() Failed ", e);
		}
		catch(Exception e){
			log.error("LC CaseEvenetHandler : onEvent() Failed due to "+e.getMessage());
			log.error("LC CaseEvenetHandler : onEvent() Failed due to ", e);

		}	finally{
		}

		log.debug("LC CaseEvenetHandler : onEvent() end ");
	}
	    	
	 void copyDocumentProperties(ObjectStore objectStore, Folder caseFolder, Document initiatingDocument, String fileRefNum)
	  {
	    /*if (this.copyDocumentProperties.booleanValue() != true)
	      return;*/
	    String caseClassName = caseFolder.getClassName();
	    String docClassName = initiatingDocument.getClassName();
	    String applicantEmail = null;
	    boolean isShift = false;

	    if (!(objectStore.getProperties().isPropertyPresent("Id")))
	    {
	      objectStore.refresh(OBJECT_ID);
	    }

	    ClassInfo caseClassInfo = ObjectStoreCache.getObjectStoreCache(objectStore.get_Id()).getClassInfo(caseClassName);
	    ClassInfo docClassInfo = ObjectStoreCache.getObjectStoreCache(objectStore.get_Id()).getClassInfo(docClassName);

	    int numProps = docClassInfo.getNumProperties();

	    for (int ix = 0; ix < numProps; ++ix)
	    {
	      PropertyInfo pi = docClassInfo.getPropertyInfo(ix);

	      if ((pi.isSystemGenerated()) || (pi.isSystemOwnedProperty()))
	        continue;
	      String symname = pi.getSymbolicPropertyName();

	      PropertyInfo casePi = caseClassInfo.getPropertyInfoNoError(symname);
	      
	      //log.debug("Document property  : " + symname);
	      if(symname.equalsIgnoreCase("LOC_IsSwiftDocument"))
		    {
	    	  	Property docProperty = initiatingDocument.getProperties().get(symname);
	    	  	try{
	    	  		isShift = docProperty.getBooleanValue();
	    	  	} catch(Exception e) {
	    	  		
	    	  	}
		  	    log.debug("isShift initialized : " + isShift);
		    }
	      
	      if (casePi == null)
	        continue;
	      int dataType = pi.getDataType();
	      int caseDataType = casePi.getDataType();

	      if ((dataType != caseDataType) || (!(initiatingDocument.getProperties().isPropertyPresent(symname))))
	        continue;
	      Property docProperty = initiatingDocument.getProperties().get(symname);

	      if (docProperty.getState().equals(PropertyState.NO_VALUE))
	        continue;
	      Properties caseProps = caseFolder.getProperties();

	      if (log.isDebugEnabled())
	      {
	        log.debug("Document Creates Case: Copy initiating document property (" + symname + ") data type (" + dataType + ") to Case folder.");
	      }
	      
	      if (pi.getCardinality().equals(Cardinality.SINGLE))
	      {
	        putSinglePropertyValue(dataType, caseProps, docProperty);
	      }
	      else
	      {
	        putPropertyValueList(dataType, caseProps, docProperty);
	      }
	    }
	    
	    if(isShift)
	    {
	    	Properties caseProps = caseFolder.getProperties();
	    	Property emailId = caseProps.get("LOC_ApplicantEmailId");
	    	applicantEmail = emailId.getStringValue();
	    	log.debug("Applicant email id : " + applicantEmail);
	    	if(null != applicantEmail && !applicantEmail.equalsIgnoreCase(""))
	    		sendEmailTo(applicantEmail, fileRefNum, initiatingDocument);
	    }
	    
	    log.debug(initiatingDocument.get_Name());
	    if(null == initiatingDocument.get_Name() || "".equalsIgnoreCase(initiatingDocument.get_Name()))
        {
	    	Date date = new Date();
	    	initiatingDocument.getProperties().putValue("DocumentTitle", "ExDoc"+date.getTime());
	    	initiatingDocument.save(RefreshMode.REFRESH);
        }
	    
	  }
	 
	 private void sendEmailTo(String applicantEmail, String fileRefNum, Document initiatingDocument) {
		  log.debug("applicantEmail given : " + applicantEmail);
		 
	      String to = applicantEmail;
	      String from = prop.getProperty("FROM_USER");
	      log.debug("from given : " + from);
	      
	      java.util.Properties properties = System.getProperties();
	      properties.setProperty("mail.smtp.host", prop.getProperty("SMTP_HOST_NAME"));
	      properties.put("mail.smtp.port", prop.getProperty("SMTP_PORT"));
	      //properties.setProperty("mail.user", prop.getProperty("SMTP_AUTH_USER"));
	      //properties.setProperty("mail.password", prop.getProperty("SMTP_AUTH_PWD"));

	      javax.mail.Session session = javax.mail.Session.getInstance(properties,
	    		  new javax.mail.Authenticator() {
	    			protected PasswordAuthentication getPasswordAuthentication() {
	    				return new PasswordAuthentication(prop.getProperty("SMTP_AUTH_USER"), prop.getProperty("SMTP_AUTH_PWD"));
	    			}
	    		  });
	      //javax.mail.Session session = javax.mail.Session.getDefaultInstance(properties);

	      try {
	    	  javax.mail.internet.MimeMessage message = new javax.mail.internet.MimeMessage(session);

	         // Set From: header field of the header.
	         message.setFrom(new InternetAddress(from));

	         // Set To: header field of the header.
	         message.addRecipient(Message.RecipientType.TO,new InternetAddress(to));

	         // Set Subject: header field
	         String mailSubject = fileRefNum + " " + prop.getProperty("EMAIL_SUBJECT");
	         message.setSubject(mailSubject);

	         // Create the message part 
	         BodyPart messageBodyPart = new MimeBodyPart();

	         // Fill the message
	         String mailBody = prop.getProperty("EMAIL_CONTENT").replace("<<LOC_REF_NUMBER>>", fileRefNum);
	         messageBodyPart.setText(mailBody);
	         
	         // Create a multipar message
	         Multipart multipart = new MimeMultipart();

	         // Set text message part
	         multipart.addBodyPart(messageBodyPart);

	         // Part two is attachment
	         messageBodyPart = new MimeBodyPart();
	         ContentElementList contentElements = initiatingDocument.get_ContentElements();
	         Iterator iter = contentElements.iterator();
	         String contentType = null;
	         ContentTransfer ct = null;
	         
	         while (iter.hasNext() )
	         {
	             ct = (ContentTransfer) iter.next();
	             break;
	         }
	         String filename = ct.get_RetrievalName();//initiatingDocument.get_Name();
	         DataSource source = new ByteArrayDataSource(ct.accessContentStream(), initiatingDocument.get_MimeType());
	         messageBodyPart.setDataHandler(new DataHandler(source));
	         /*log.debug("filename : " + filename);
	         log.debug("RetrievalName : " + ct.get_RetrievalName());
	         log.debug("Retrieval type : " + ct.get_ContentType());
	         log.debug("Retrieval type : " + ct.getProperties());
	         log.debug("initiatingDocument.get_MimeType() : " + initiatingDocument.get_MimeType());*/
	         messageBodyPart.setFileName(filename);
	         multipart.addBodyPart(messageBodyPart);

	         // Send the complete message parts
	         message.setContent(multipart );

	         // Send message
	         Transport.send(message);
	         log.debug("Sent message successfully....");
	      }catch (MessagingException mex) {
	    	  log.error("Exception occured while sending mail ", mex);
	      } catch (IOException e) {
	    	  log.error("IO Exception occured while accessing content sending mail ", e);
		}
	}


	private void putPropertyValueList(int dataType, Properties caseProps, Property docProperty)
	  {
	    String propName = docProperty.getPropertyName();

	    switch (dataType)
	    {
	    case 2:
	      caseProps.putValue(propName, docProperty.getBooleanListValue());
	      break;
	    case 3:
	      caseProps.putValue(propName, docProperty.getDateTimeListValue());
	      break;
	    case 4:
	      caseProps.putValue(propName, docProperty.getFloat64ListValue());
	      break;
	    case 5:
	      caseProps.putValue(propName, docProperty.getIdListValue());
	      break;
	    case 6:
	      caseProps.putValue(propName, docProperty.getInteger32ListValue());
	      break;
	    case 8:
	      caseProps.putValue(propName, docProperty.getStringListValue());
	      break;
	    case 1:
	      caseProps.putValue(propName, docProperty.getBinaryListValue());
	      break;
	    case 7:
	    default:
	      throw new EngineRuntimeException(ExceptionCode.E_FAILED);
	    }
	  }

	  private void putSinglePropertyValue(int dataType, Properties caseProps, Property docProperty)
	  {
	    String propName = docProperty.getPropertyName();

	    switch (dataType)
	    {
	    case 2:
	      caseProps.putValue(propName, docProperty.getBooleanValue());
	      break;
	    case 3:
	      caseProps.putValue(propName, docProperty.getDateTimeValue());
	      break;
	    case 4:
	      caseProps.putValue(propName, docProperty.getFloat64Value());
	      break;
	    case 5:
	      caseProps.putValue(propName, docProperty.getIdValue());
	      break;
	    case 6:
	      caseProps.putValue(propName, docProperty.getInteger32Value());
	      break;
	    case 8:
	      caseProps.putValue(propName, docProperty.getStringValue());
	      break;
	    case 1:
	      caseProps.putValue(propName, docProperty.getBinaryValue());
	      break;
	    case 7:
	      caseProps.putObjectValue(propName, docProperty.getObjectValue());
	      break;
	    default:
	      throw new EngineRuntimeException(ExceptionCode.E_FAILED);
	    }
	  }


}
