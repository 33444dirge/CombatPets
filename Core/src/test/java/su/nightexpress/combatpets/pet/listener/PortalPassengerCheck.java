package su.nightexpress.combatpets.pet.listener;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import su.nightexpress.combatpets.pet.PetManager;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Standalone regression and optional model microbenchmark; never loaded by the plugin. */
public final class PortalPassengerCheck {
    private static int copies;
    private static int identities;
    private static volatile int sink;

    private static final class Node {
        final boolean pet;
        final List<Entity> passengers = new ArrayList<>();
        final Entity entity;

        Node(boolean living, boolean pet) {
            this.pet = pet;
            InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
                case "isEmpty" -> passengers.isEmpty();
                case "getPassengers" -> { copies++; yield new ArrayList<>(passengers); }
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "fixture";
                default -> throw new AssertionError("Unexpected entity access: " + method.getName());
            };
            entity = (Entity) Proxy.newProxyInstance(Entity.class.getClassLoader(),
                new Class<?>[]{living ? LivingEntity.class : Entity.class, Marker.class},
                (proxy, method, args) -> method.getName().equals("node") ? this : handler.invoke(proxy, method, args));
        }
    }

    public interface Marker { Object node(); }
    private static Node node(Entity entity) { return (Node) ((Marker) entity).node(); }

    private static MethodHandle target() throws Exception {
        PetManager manager = new PetManager(null) {
            @Override public boolean isPetEntity(LivingEntity entity) {
                identities++;
                return node(entity).pet;
            }
        };
        PetGenericListener listener = new PetGenericListener(null, manager);
        return MethodHandles.privateLookupIn(PetGenericListener.class, MethodHandles.lookup())
            .findVirtual(PetGenericListener.class, "containsPet", MethodType.methodType(boolean.class, Entity.class))
            .bindTo(listener);
    }

    private static boolean expected(Node node) {
        if (node.pet) return true;
        for (Entity child : node.passengers) if (expected(node(child))) return true;
        return false;
    }

    private static Node tree(Random random, int depth) {
        boolean living = random.nextBoolean();
        Node node = new Node(living, living && random.nextInt(12) == 0);
        if (depth > 0) for (int i = random.nextInt(4); i > 0; i--) node.passengers.add(tree(random, depth - 1).entity);
        return node;
    }

    private static void verify(MethodHandle target, Node node) throws Throwable {
        boolean actual = (boolean) target.invokeExact(node.entity);
        if (actual != expected(node)) throw new AssertionError("Passenger predicate changed");
    }

    public static void main(String[] args) throws Throwable {
        MethodHandle target = target();
        Node empty = new Node(false, false), mob = new Node(true, false), pet = new Node(true, true);
        Node vehicle = new Node(false, false), nested = new Node(false, false);
        int checks = 0;
        for (Node node : List.of(empty, mob, pet)) { verify(target, node); checks++; }
        vehicle.passengers.add(mob.entity); verify(target, vehicle); checks++;
        nested.passengers.add(vehicle.entity); verify(target, nested); checks++;
        vehicle.passengers.add(pet.entity); verify(target, nested); checks++;
        vehicle.passengers.remove(pet.entity); verify(target, nested); checks++;
        vehicle.passengers.clear(); verify(target, nested); checks++;
        Random random = new Random(20261015L);
        for (int i = 0; i < 20000; i++) { verify(target, tree(random, 5)); checks++; }
        // An already recognized pet must still short circuit without inspecting its passengers.
        copies = identities = 0;
        verify(target, pet);
        checks++;
        if (copies != 0 || identities != 1) throw new AssertionError("Pet short circuit changed");
        if (args.length == 0) {
            copies = 0;
            verify(target, empty); verify(target, mob);
            checks += 2;
            if (copies != 0) throw new AssertionError("Empty entities still copy passenger lists");
        }
        System.out.println("PORTAL_REGRESSION_PASS checks=" + checks);
        if (args.length > 0 && args[0].equals("bench")) {
            vehicle.passengers.add(mob.entity);
            Node petVehicle = new Node(false, false); petVehicle.passengers.add(pet.entity);
            Node deep = new Node(false, false), tail = deep;
            for (int i = 0; i < 16; i++) { Node next = new Node(false, false); tail.passengers.add(next.entity); tail = next; }
            bench(target, "empty", empty);
            bench(target, "mob", mob);
            bench(target, "pet", pet);
            bench(target, "ordinary_passenger", vehicle);
            bench(target, "pet_passenger", petVehicle);
            bench(target, "nested_empty", deep);
        }
    }

    private static void bench(MethodHandle target, String name, Node node) throws Throwable {
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (Thread.currentThread().isVirtual() || !bean.isThreadAllocatedMemorySupported()
            || !bean.isCurrentThreadCpuTimeSupported()) throw new AssertionError("Unsupported measurement thread");
        bean.setThreadAllocatedMemoryEnabled(true); bean.setThreadCpuTimeEnabled(true);
        int count = 300000;
        for (int i = 0; i < 5; i++) run(target, node.entity, count);
        for (int round = 0; round < 7; round++) {
            long bytes = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
            long cpu = bean.getCurrentThreadCpuTime(), start = System.nanoTime();
            run(target, node.entity, count);
            long elapsed = System.nanoTime() - start, usedCpu = bean.getCurrentThreadCpuTime() - cpu;
            long allocated = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - bytes;
            System.out.printf(java.util.Locale.ROOT, "BENCH case=%s round=%d ns=%.2f cpu_ns=%.2f bytes=%.2f%n",
                name, round, (double) elapsed / count, (double) usedCpu / count, (double) allocated / count);
        }
    }

    private static void run(MethodHandle target, Entity entity, int count) throws Throwable {
        int found = 0;
        for (int i = 0; i < count; i++) if ((boolean) target.invokeExact(entity)) found++;
        sink = found;
    }
}
