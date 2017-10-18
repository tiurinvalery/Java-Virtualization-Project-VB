import java.util.Arrays;
import java.util.List;
import org.virtualbox_4_2.CleanupMode;
import org.virtualbox_4_2.IEvent;
import org.virtualbox_4_2.IEventListener;
import org.virtualbox_4_2.IMachine;
import org.virtualbox_4_2.IMachineRegisteredEvent;
import org.virtualbox_4_2.IMachineStateChangedEvent;
import org.virtualbox_4_2.IProgress;
import org.virtualbox_4_2.ISession;
import org.virtualbox_4_2.ISessionStateChangedEvent;
import org.virtualbox_4_2.SessionState;
import org.virtualbox_4_2.VBoxEventType;
import org.virtualbox_4_2.VirtualBoxManager;

public class Events_4_2 {
    static VirtualBoxManager mgr;
    static Thread listener;
    final static String url = "http://localhost:18083";
  //  final static String vmName = Long.toString(System.currentTimeMillis()); // get a random VM name to use later
    final static String vmName="linux";
    public static void main(String args []){
        User valera = User.createUser("linux","55555");
        User.AddUser(valera);
        valera = User.getUser(valera);
        try{
            connect(valera);
            listener = new EventWorker();
            listener.start();
            try{
                //createMachine(null,vmName,null,"Other",null);
                IMachine vm = mgr.getVBox().findMachine(vmName);
                createProcess(vm);
                deleteMachine(vm);
            }finally { // we instruct the Event Worker to stop and wait 5 sec for it to do so
                listener.interrupt();
                try {
                    listener.join(5000); // we wait on the thread
                } catch (InterruptedException e) {
                    System.err.println("Interrupted while waiting for EventWorker to stop");
                }

                if (listener.isAlive()) {
                    System.err.println("Event worked did not stop in a timely fashion");
                } else {
                    System.out.println("Event worked stopped");
                }
            }
        }finally {
           // mgr.disconnect(); // only if you are using WebServices Bindings
            mgr.cleanup();
            System.out.println("Disconnected from VirtualBox - bye bye!");
        }
        }
        public static void connect(User u){
            System.out.println("Creating VirtualBox client instance");
            mgr = VirtualBoxManager.createInstance(null);
            System.out.println("Connecting to VirtualBox using Web Services");
            //mgr.connect(url , u.userName,u.pass ); // only if you are using WebServices Bindings
            System.out.println("Connected to VirtualBox using Web Services");
     }
        public static void createMachine(String settingsFile, String name , List<String> groups , String OsID, String flags){
            IMachine vm = mgr.getVBox().createMachine(settingsFile, name, groups, OsID,flags);//for Install OS change osTypeId
            vm.saveSettings();
            mgr.getVBox().registerMachine(vm);
        }
        public static void createProcess(IMachine vm){
            ISession session = mgr.getSessionObject();
            IProgress p = vm.launchVMProcess(session, "headless", null);
            p.waitForCompletion(-1); // we wait until the starting process has finished
            try {
                if (p.getResultCode() != 0) { // error when starting the VM, we don't continue further
                    throw new RuntimeException(p.getErrorInfo().getText());
                } else { // VM got started, OnMachineStateChanged triggered two times at this point
                    // let's now power down the VM
                    p = session.getConsole().powerDown();
                    p.waitForCompletion(-1);
                    if (p.getResultCode() != 0) { // we failed to stop the VM
                        throw new RuntimeException(p.getErrorInfo().getText());
                    } else {
                        // VM got stopped, OnMachineStateChanged triggered several times at this point
                    }
                }
            } finally {
                // we do not need the lock any further and is required for for IMachine::unregister()
                session.unlockMachine();

                // since unlock is not instant, we need to wait until the unlock is done or unregister() will fail.
                while (!SessionState.Unlocked.equals(vm.getSessionState())) {
                    try {
                        System.out.println("Waiting for session unlock...");
                        Thread.sleep(1000L);
                    } catch (InterruptedException e) {
                        System.err.println("Interrupted while waiting for session to be unlocked");
                    }
                }
            }
        }
        public static void deleteMachine(IMachine vm){
            System.out.println("Deleting machine");
            vm.delete(vm.unregister(CleanupMode.DetachAllReturnHardDisksOnly));
        }


        static class EventWorker extends Thread {

             IEventListener el;

                @Override
                public void run() {
                    System.out.println("EventWorker started");
                    el = mgr.getVBox().getEventSource().createListener(); // we create the object that will fetch and queue the events for us

            /*
             * We register for the events that we are interested in.
             * If we wanted all of them, we would use VBoxEventType.Any
             *
             * Not all events will be visible here. If we want events for a specific machine (like Clipboard mode change),
             * we need to use the event source of that specific machine - see IMachine::getEventSource()
             */
                    List<VBoxEventType> types = Arrays.asList(VBoxEventType.OnSessionStateChanged, VBoxEventType.OnMachineStateChanged,
                            VBoxEventType.OnMachineRegistered);
                    mgr.getVBox().getEventSource().registerListener(el, types, false);

                    try {
                        while(!isInterrupted()) {
                            mgr.waitForEvents(0); // Needed to clear the internal event queue, see https://www.virtualbox.org/ticket/13647
                            IEvent rawEvent = mgr.getVBox().getEventSource().getEvent(el, 1000);
                            if (rawEvent == null) { // we waited but no event came
                                continue; // we loop again and skip the code below
                            }

                            try {
                                System.out.println("Got event of type " + rawEvent.getType());

                                if (VBoxEventType.OnSessionStateChanged.equals(rawEvent.getType())) {
                            // It is important to use the queryInterface() on the expected class, simple casting will not work.
                                    ISessionStateChangedEvent event = ISessionStateChangedEvent.queryInterface(rawEvent);
                                 System.out.println("Session state changed to " + event.getState() + " for machine " + event.getMachineId());
                            }

                                if (VBoxEventType.OnMachineRegistered.equals(rawEvent.getType())) { // we check the event type
                            // It is important to use the queryInterface() on the expected class, simple casting will not work.
                                    IMachineRegisteredEvent event = IMachineRegisteredEvent.queryInterface(rawEvent);
                                    System.out.println("Machine " + event.getMachineId() + " has been " + (event.getRegistered() ? "registered" : "unregistered"));
                                }

                                if (VBoxEventType.OnMachineStateChanged.equals(rawEvent.getType())) {
                            // It is important to use the queryInterface() on the expected class, simple casting will not work.
                                    IMachineStateChangedEvent event = IMachineStateChangedEvent.queryInterface(rawEvent);
                                    System.out.println("Machine " + event.getMachineId() + " state changed to " + event.getState());
                                }
                            } finally {
                        // We mark the event as processed so ressources can be released. We do this in a finally block to ensure it is done no matter what.
                                mgr.getVBox().getEventSource().eventProcessed(el, rawEvent);
                            }
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    } finally {
                        mgr.getVBox().getEventSource().unregisterListener(el); // we are done fetching events, so we free the listener
                     System.out.println("EventWorker finished");
                    }
             }

            }






    }
