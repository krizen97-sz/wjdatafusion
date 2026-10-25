import java.net.InetAddress;
import java.net.Socket;
import java.util.Set;

/** Test only: guard installation is local to this child and cannot be removed. */
public final class MetadataGuardContract {
    public static void main(String[] args) throws Exception {
        var guard = new OriginalConnectionForms.MetadataNetworkGuard(Set.of("127.0.0.1:" + args[0]));
        System.setSecurityManager(guard);
        boolean remove = false, replace = false, dns = false, endpoint = false;
        try { System.setSecurityManager(null); } catch (SecurityException expected) { remove = true; }
        try { System.setSecurityManager(guard); } catch (SecurityException expected) { replace = true; }
        try { InetAddress.getByName("must-not-resolve.invalid"); } catch (SecurityException expected) { dns = true; }
        try (Socket ignored = new Socket("127.0.0.1", Integer.parseInt(args[1]))) { }
        catch (SecurityException expected) { endpoint = true; }
        if (!(remove && replace && dns && endpoint && System.getSecurityManager() == guard)) throw new AssertionError("guard boundary");
        System.out.println("GUARD_BOUNDARY_CONFIRMED");
    }
}
