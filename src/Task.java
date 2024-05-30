import java.io.Serializable;
import java.util.List;

public class Task implements Serializable {

    private String id;
    private String command;
    private List<String> result;

    public Task(String id, String command, List<String> result) {
        this.id=id;
        this.command = command;
        this.result = result;
    }

    public String getCommand() {
        return command;
    }

    public String getId() {
        return id;
    }

    public List<String> getResult() {
        return result;
    }
}
