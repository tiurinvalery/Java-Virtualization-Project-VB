import org.junit.Test;

import java.io.ByteArrayOutputStream;


public class connectTest {
    @Test
    public void connection() throws Exception{
        User u = User.createUser("1","1");
        Events_4_2.connect(u);


    }
}
