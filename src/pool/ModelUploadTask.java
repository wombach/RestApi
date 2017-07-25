package pool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Logger;

import org.iea.connector.parser.ParserFactory;
import org.iea.pool.TaskState;
import org.iea.pool.TaskStatus;

public class ModelUploadTask implements Runnable {
	private final static Logger LOGGER = Logger.getLogger(ModelUploadTask.class.getName());
	private ParserFactory pf;
	private String taskId;
	private String intFileName;
	private String projectName;
	private String branchName;
	private String userid;
	private TaskStatus taskStatus;
	private String parserName;
	private String contentType;

	public ModelUploadTask(TaskStatus taskStatus, String taskId, ParserFactory pf, String intFileName,
			String contentType, String parserName, String projectName, String branchName, String userid) {
		this.taskStatus = taskStatus;
		this.taskId = taskId;
		this.pf = pf;
		this.intFileName = intFileName;
		this.projectName = projectName;
		this.branchName = branchName;
		this.userid = userid;
		this.parserName = parserName;
		this.contentType = contentType;
	}

    
    public void run() {
    	try{
    	LOGGER.info("start processing task "+taskId);
    	taskStatus.setMsg("start processing");
    	pf.addTaskStatus(taskId, taskStatus);
    	String json = readFile(intFileName);
    	if(contentType.equals("text/xml")){
        	json = pf.convertXMLtoJSON(taskId, parserName, json);
        } else if(contentType.equals("application/json")){
        	// nothing to be done
        } else {
        	taskStatus.setMsg("unsupported uploaded file content type: "+contentType);
        	taskStatus.setState(TaskState.FAILURE);
        	pf.addTaskStatus(taskId, taskStatus);
            return;
        }
    	
        taskStatus.setMsg("start model insertion");
        pf.addTaskStatus(taskId, taskStatus);
        pf.processJsonString(taskId, parserName, projectName, branchName, userid, json, false);
        taskStatus.setMsg("model insertion complete");
        taskStatus.setState(TaskState.COMPLETED);
        pf.addTaskStatus(taskId, taskStatus);
        } catch(Exception e){
    		taskStatus.setState(TaskState.FAILURE);
    		taskStatus.setMsg(e.getMessage());
    		pf.addTaskStatus(taskId, taskStatus);
        }
    }
    
	private String readFile(String filename){
		String content = null;
		try {
			content = new String(Files.readAllBytes(Paths.get(filename)));
		} catch (IOException e) {
			e.printStackTrace();
			taskStatus.setState(TaskState.FAILURE);
    		taskStatus.setMsg(e.getMessage());
    		pf.addTaskStatus(taskId, taskStatus);
		}
		return content;
	}
}
