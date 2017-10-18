import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class CreateUserTest extends Assert {

    @Test
    public void addUserTest() {
        User u = new User("Inna","4444");
        User.AddUser(u);
        boolean f = true;
        boolean t  = User.users.contains(u);
        assertEquals(f,t);

    }

}
