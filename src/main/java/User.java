import java.util.ArrayList;
import java.util.List;

//#
public class User {
     public String userName;
     public String pass;
     static ArrayList<User> users = new ArrayList<User>();

    User(){
        userName="user";
        pass = "root";
    }
    User(String name , String password){
        userName=name;
        pass=password;
    }
    private static  boolean Unique(User u){
        if(users.contains(u)) return false;
        return true;
    }
    public static void AddUser(User nUser){
        if(Unique(nUser)){
            users.add(nUser);
        }
        else{
            System.out.println("User already exists");
        }
    }
    public static User getUser(User user){
        if(users.contains(user)){
            return user;
        }
        else{
            System.out.println("Sorry,user don't exists");
            return null;
        }
    }
    public static User createUser(String name, String password){
        User user = new User(name,password);
        return user;
    }

}
