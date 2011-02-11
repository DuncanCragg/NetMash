
package jungle.platform;

/** Module has a callback from Kernel.
  */
public interface Module {

    public void run();
    public void threadedObject(Object event);
}

