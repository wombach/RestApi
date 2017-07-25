import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.annotation.WebInitParam;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.output.*;
import org.iea.connector.parser.Archimate3Parser;
import org.iea.connector.parser.ParserFactory;
import org.iea.connector.parser.storage.Archimate3MongoDBConnector;
import org.iea.pool.TaskState;
import org.iea.pool.TaskStatus;

import pool.ModelUploadTask;

/**
 * Servlet implementation class FileUpload
 * @param <TaskStatus>
 */
// @WebServlet(urlPatterns = "/FileUpload")
@WebServlet(name="FileUpload",
urlPatterns={"/model"},
 initParams= { @WebInitParam(name="file-upload", value="/tmp/upload/"), 
		@WebInitParam(name="n2", value="v2") 
}
)
@MultipartConfig
public class FileUpload extends HttpServlet {
	private final static Logger LOGGER = Logger.getLogger(FileUpload.class.getName());
	private boolean isMultipart;
	private String filePath;
	private int maxFileSize = 50 * 1024 * 1024;
	private int maxMemSize = 4 * 1024;
	private File file ;
	public ParserFactory pf = new ParserFactory();
	//public ConcurrentHashMap<String, TaskStatus> statusMap; 
	//public ThreadPool pool = null;
	ExecutorService executor;

	public void init( ServletConfig config) throws ServletException{
		super.init(config);
		// Get the file location where it would be stored.
		filePath = config.getInitParameter("file-upload");
		LOGGER.info(filePath);
		if (filePath==null || filePath.isEmpty()) {
			filePath = "c:\\temp\\upload";
		}
//		try {
//			filePath = Files.createTempDirectory("upload").toAbsolutePath().toString()+File.pathSeparator;
//		} catch (IOException e) {
//			e.printStackTrace();
//			throw new ServletException(e.getMessage());
//		}
		LOGGER.info(filePath);
		
		pf.registerParser("archimate3", new Archimate3Parser());
		pf.registerStorage("archimate3", new Archimate3MongoDBConnector(), true);
		//		pf.registerStorage("archimate3", new Archimate3Neo4jConnector(), false);
		//		pf.registerParser("archimate", new ArchimateParser());
		//pool = new ThreadPool(7);
		executor = Executors.newFixedThreadPool(4);
	}

	public void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, java.io.IOException {

		// Check that we have a file upload request
		isMultipart = ServletFileUpload.isMultipartContent(request);
		response.setContentType("text/html");
		java.io.PrintWriter out = response.getWriter( );
		String comment = "no comment";
		String intFileName = null;
		String parserName = null;
		String projectName = null;
		String branchName = null;
		String userid = null;
		String contentType = null;

		if( !isMultipart ) {
			out.println("<html>");
			out.println("<head>");
			out.println("<title>Servlet upload</title>");  
			out.println("</head>");
			out.println("<body>");
			out.println("<p>No file uploaded</p>"); 
			out.println("</body>");
			out.println("</html>");
			return;
		}

		DiskFileItemFactory factory = new DiskFileItemFactory();

		// maximum size that will be stored in memory
		factory.setSizeThreshold(maxMemSize);

		// Location to save data that is larger than maxMemSize.
		factory.setRepository(new File("/tmp"));

		// Create a new file upload handler
		ServletFileUpload upload = new ServletFileUpload(factory);

		// maximum file size to be uploaded.
		upload.setSizeMax( maxFileSize );

		try { 
			// Parse the request to get file items.
			List fileItems = upload.parseRequest(request);

			// Process the uploaded file items
			Iterator i = fileItems.iterator();

			out.println("<html>");
			out.println("<head>");
			out.println("<title>Servlet upload</title>");  
			out.println("</head>");
			out.println("<body>");
			String uuid = UUID.randomUUID().toString();

			while ( i.hasNext () ) {
				FileItem fi = (FileItem)i.next();
				if ( !fi.isFormField () ) {
					// Get the uploaded file parameters
					String fieldName = fi.getFieldName();
					String fileName = fi.getName();
//					contentType = fi.getContentType();
//					LOGGER.info("file content type: "+contentType);
					boolean isInMemory = fi.isInMemory();
					long sizeInBytes = fi.getSize();

					// Write the file
					if( fileName.lastIndexOf(File.pathSeparator) >= 0 ) {
						//file = new File(fileName);
						file = new File( filePath + fileName.substring( fileName.lastIndexOf(File.pathSeparator))) ;
					} else {
						file = new File( filePath + fileName.substring(fileName.lastIndexOf(File.pathSeparator)+1)) ;
					}
					intFileName = filePath + uuid +".json";
					file = new File(intFileName);

					fi.write( file ) ;
					out.println("Uploaded Filename: " + fileName + "<br>");
				} else {
					String name = fi.getFieldName();//text1
					String value = fi.getString();
					if (name.equals("comment")){
						comment = value;
					} else if (name.equals("parserName")){
						parserName = value;
					} else if (name.equals("projectName")){
						projectName = value;
					} else if (name.equals("branchName")){
						branchName = value;
					} else if (name.equals("contentType")){
						contentType = value;
					} else if (name.equals("userid")){
						userid = value;
					} 
				}
			} // end of while
			if(intFileName!=null && projectName!=null && branchName!=null
					&& parserName!=null && userid!=null){
				TaskStatus taskStatus = new TaskStatus(0,0,0,"file uploaded");
				pf.changeStatus(uuid, taskStatus);
				ModelUploadTask task = new ModelUploadTask(taskStatus, uuid, pf, intFileName, contentType,
						parserName, projectName, branchName, userid);
				//pool.execute(task);
				executor.execute(task);
				out.println("taskId: "+uuid);
			}
			out.println("</body>");
			out.println("</html>");
		} catch(Exception ex) {
			LOGGER.severe(ex.getMessage());
		}
	}

	/**
	 * retrieve a version of a model
	 * 
	 * projectName
	 * branchName
	 * userid
	 * version: optional
	 */
	public void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, java.io.IOException {
		String projectName = null;
		String parserName = null;
		String branchName = null;
		String userid = null;
		String version = null;
		String taskId = null; 
		String contentType = null;
		
		String[] strArr = request.getParameterValues("projectName");
		if(strArr!=null && strArr.length==1 && !strArr[0].isEmpty()) projectName = strArr[0];
		strArr = request.getParameterValues("parserName");
		if(strArr!=null && strArr.length==1 && !strArr[0].isEmpty()) parserName = strArr[0];
		strArr = request.getParameterValues("branchName");
		if(strArr!=null && strArr.length==1 && !strArr[0].isEmpty()) branchName = strArr[0];
		strArr = request.getParameterValues("userid");
		if(strArr!=null && strArr.length==1 && !strArr[0].isEmpty()) userid = strArr[0];
		strArr = request.getParameterValues("version");
		if(strArr!=null && strArr.length==1 && !strArr[0].isEmpty()) version = strArr[0];
		strArr = request.getParameterValues("contentType");
		if(strArr!=null && strArr.length==1 && !strArr[0].isEmpty()) contentType = strArr[0];
		strArr = request.getParameterValues("taskId");
		if(strArr!=null && strArr.length==1 && !strArr[0].isEmpty()) taskId = strArr[0];
		
		LOGGER.info("projectName: "+projectName);
		LOGGER.info("parserName: "+projectName);
		LOGGER.info("branchName: "+branchName);
		LOGGER.info("userid: "+userid);
		LOGGER.info("version: "+version);
		LOGGER.info("contentType: "+contentType);
		LOGGER.info("taskId: "+taskId);
		
		if(taskId !=null && !taskId.isEmpty()){
			org.iea.pool.TaskStatus taskStatus = pf.getTaskStatus(taskId);
			String statusMsg = taskStatus.getJsonObjectString();
			response.setContentType("application/json");
			PrintWriter out = response.getWriter();
			// Assuming your json object is **jsonObject**, perform the following, it will return your json object  
			response.setCharacterEncoding("UTF-8");
			out.print(statusMsg);
			out.flush();
			out.close();
		} else {
//			response.setContentType("application/json");
			taskId = UUID.randomUUID().toString();
			Date date = new Date(System.currentTimeMillis());
			String resp = pf.retrieveJsonString(taskId, parserName, projectName, branchName, userid, date);
			
			if(contentType.equals("application/json")){
				response.setHeader("Content-disposition","attachment; filename="+taskId+".json");
			} else if(contentType.equals("text/xml")){
				response.setHeader("Content-disposition","attachment; filename="+taskId+".xml");
				resp = pf.convertJSONtoXML(taskId, parserName, resp);
			} else {
//				TaskStatus taskStatus = new TaskStatus();
//	    		taskStatus.setState(TaskState.FAILURE);
//	    		taskStatus.setMsg("requested content type is not supported yet: "+contentType);
//	    		pf.addTaskStatus(taskId, taskStatus);
	    		response.setContentType("text/html");
				resp = "<html><body>Download failed; requested content type not supported: "+contentType+"</body></html>";
			}
			PrintWriter out = response.getWriter();
			response.setCharacterEncoding("UTF-8");
			out.print(resp);
			out.flush();
			out.close();
		}


	}

}
