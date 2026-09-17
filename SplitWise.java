import java.util.*;
import java.text.SimpleDateFormat;

// Entry point class (contains main)
public class SplitwiseLLD {
    public static void main(String[] args) {
        // Create a BalanceRepository instance
        BalanceRepository balanceRepo = new BalanceRepository();
        
        // Create services
        ExpenseService expenseService = new ExpenseService(balanceRepo);
        SettlementService settlementService = new SettlementService(balanceRepo);
        
        // Create users
        User u1 = new User("U1", "Alice", "alice@example.com");
        User u2 = new User("U2", "Bob",   "bob@example.com");
        User u3 = new User("U3", "Carol", "carol@example.com");

        // Create a group and add members
        Group group = new Group("G1", "Trip");
        group.addMember(u1);
        group.addMember(u2);
        group.addMember(u3);
        
        // Expense 1: Alice pays $300 for everyone (equal split)
        List<Participant> participants1 = Arrays.asList(
            new Participant(u1, 0, 0),
            new Participant(u2, 0, 0),
            new Participant(u3, 0, 0)
        );
        Expense expense1 = new Expense("E1", "Hotel", 300.0, u1, new EqualSplit(), participants1);
        group.addExpense(expense1);
        expenseService.addExpense(group, expense1);

        // Expense 2: Bob pays $150 for Alice and Bob with exact amounts
        List<Participant> participants2 = Arrays.asList(
            new Participant(u1, 100.0, 0),
            new Participant(u2, 50.0, 0)
        );
        Expense expense2 = new Expense("E2", "Dinner", 150.0, u2, new ExactSplit(), participants2);
        group.addExpense(expense2);
        expenseService.addExpense(group, expense2);

        // Expense 3: Carol pays $120 for everyone with specified percentages
        List<Participant> participants3 = Arrays.asList(
            new Participant(u1, 0, 50.0),
            new Participant(u2, 0, 30.0),
            new Participant(u3, 0, 20.0)
        );
        Expense expense3 = new Expense("E3", "Taxi", 120.0, u3, new PercentageSplit(), participants3);
        group.addExpense(expense3);
        expenseService.addExpense(group, expense3);

        // Display net balances for each user in the group
        System.out.println("Net balances:");
        for (User user : group.getMembers()) {
            double net = balanceRepo.getNetBalance(user.getId(), group.getId());
            System.out.println(user.getName() + " net balance: " + net);
        }

        // Simplify debts and display the settlements
        System.out.println("\nSimplified Settlements:");
        List<Settlement> settlements = settlementService.simplifyDebts(group);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (Settlement st : settlements) {
            System.out.println("From: " + st.getFromUserId() +
                               " To: "   + st.getToUserId() +
                               " Amount: " + st.getAmount() +
                               " Timestamp: " + sdf.format(st.getTimestamp()));
        }
    }
}

// Domain entity classes

class User {
    private String id;
    private String name;
    private String email;
    public User(String id, String name, String email) {
        this.id = id; this.name = name; this.email = email;
    }
    public String getId() { return id; }
    public String getName() { return name; }
    // Email getter omitted for brevity
}

class Group {
    private String id;
    private String name;
    private List<User> members;
    private List<Expense> expenses;
    public Group(String id, String name) {
        this.id = id;
        this.name = name;
        this.members = new ArrayList<>();
        this.expenses = new ArrayList<>();
    }
    public String getId() { return id; }
    public String getName() { return name; }
    public List<User> getMembers() { return members; }
    public List<Expense> getExpenses() { return expenses; }
    public void addMember(User user) { members.add(user); }
    public void addExpense(Expense expense) { expenses.add(expense); }
}

class Participant {
    private User user;
    private double exactAmount;   // for exact split
    private double percentage;    // for percentage split
    public Participant(User user, double exactAmount, double percentage) {
        this.user = user;
        this.exactAmount = exactAmount;
        this.percentage = percentage;
    }
    public String getUserId() { return user.getId(); }
    public double getExactAmount() { return exactAmount; }
    public double getPercentage() { return percentage; }
}

class Expense {
    private String id;
    private String description;
    private double totalAmount;
    private User paidBy;
    private SplitStrategy splitStrategy;
    private List<Participant> participants;

    public Expense(String id, String description, double totalAmount,
                   User paidBy, SplitStrategy splitStrategy,
                   List<Participant> participants) {
        this.id = id;
        this.description = description;
        this.totalAmount = totalAmount;
        this.paidBy = paidBy;
        this.splitStrategy = splitStrategy;
        this.participants = participants;
    }
    public String getId() { return id; }
    public String getDescription() { return description; }
    public double getTotalAmount() { return totalAmount; }
    public User getPaidBy() { return paidBy; }
    public SplitStrategy getSplitStrategy() { return splitStrategy; }
    public List<Participant> getParticipants() { return participants; }
}

// Balance repository (tracks net balances per group)
class BalanceRepository {
    // Map: groupId -> (userId -> net balance)
    private Map<String, Map<String, Double>> netBalancesByGroup = new HashMap<>();

    // Update balances when a user owes another user 'amount' in a group
    public void updateBalance(String groupId, String fromUserId, String toUserId, double amount) {
        netBalancesByGroup.putIfAbsent(groupId, new HashMap<>());
        Map<String, Double> netBal = netBalancesByGroup.get(groupId);
        // Decrease fromUser's net balance, increase toUser's
        netBal.put(fromUserId, netBal.getOrDefault(fromUserId, 0.0) - amount);
        netBal.put(toUserId,   netBal.getOrDefault(toUserId, 0.0) + amount);
    }

    // Get net balance for a user in a group
    public double getNetBalance(String userId, String groupId) {
        if (!netBalancesByGroup.containsKey(groupId)) return 0.0;
        return netBalancesByGroup.get(groupId).getOrDefault(userId, 0.0);
    }
}

// Service to handle expense creation and update balances
class ExpenseService {
    private final BalanceRepository balanceRepo;
    public ExpenseService(BalanceRepository balanceRepo) {
        this.balanceRepo = balanceRepo;
    }

    // Add an expense to a group and update balances accordingly
    public void addExpense(Group group, Expense expense) {
        Map<String, Double> shares = expense.getSplitStrategy()
            .calculateShares(expense.getTotalAmount(), expense.getParticipants());
        for (Map.Entry<String, Double> entry : shares.entrySet()) {
            String userId = entry.getKey();
            double share = entry.getValue();
            // Skip if the payer is the same user (they don't owe themselves)
            if (userId.equals(expense.getPaidBy().getId())) continue;
            // User 'userId' owes 'expense.getPaidBy()' an amount
            balanceRepo.updateBalance(group.getId(), userId, expense.getPaidBy().getId(), share);
        }
    }
}

// Service to compute debt settlements (simplification)
class SettlementService {
    private BalanceRepository balanceRepo;
    public SettlementService(BalanceRepository balanceRepo) {
        this.balanceRepo = balanceRepo;
    }

    // Return a list of settlement transactions to settle all debts in a group
    public List<Settlement> simplifyDebts(Group group) {
        // Compute net balance for each member
        Map<String, Double> netBalance = new HashMap<>();
        for (User member : group.getMembers()) {
            double net = balanceRepo.getNetBalance(member.getId(), group.getId());
            netBalance.put(member.getId(), net);
        }
        // Separate into creditors (+) and debtors (-)
        List<double[]> creditors = new ArrayList<>();
        List<double[]> debtors   = new ArrayList<>();
        List<String> ids = new ArrayList<>(netBalance.keySet());
        for (String id : ids) {
            double bal = netBalance.get(id);
            if (bal > 0.01) {
                creditors.add(new double[]{ids.indexOf(id), bal});
            } else if (bal < -0.01) {
                debtors.add(new double[]{ids.indexOf(id), -bal});
            }
        }
        // Sort by amount descending
        creditors.sort((a, b) -> Double.compare(b[1], a[1]));
        debtors.sort((a, b)   -> Double.compare(b[1], a[1]));

        // Greedy match largest creditor with largest debtor
        List<Settlement> settlements = new ArrayList<>();
        int i = 0, j = 0;
        while (i < creditors.size() && j < debtors.size()) {
            double amount = Math.min(creditors.get(i)[1], debtors.get(j)[1]);
            String credId = ids.get((int) creditors.get(i)[0]);
            String debtId = ids.get((int) debtors.get(j)[0]);
            settlements.add(new Settlement(debtId, credId, amount));
            creditors.get(i)[1] -= amount;
            debtors.get(j)[1]   -= amount;
            if (creditors.get(i)[1] < 0.01) i++;
            if (debtors.get(j)[1]   < 0.01) j++;
        }
        return settlements;
    }
}

// Represents a settlement (a payment from one user to another)
class Settlement {
    private String fromUserId;
    private String toUserId;
    private double amount;
    private Date timestamp;
    public Settlement(String fromUserId, String toUserId, double amount) {
        this.fromUserId = fromUserId;
        this.toUserId   = toUserId;
        this.amount     = amount;
        this.timestamp  = new Date();
    }
    public String getFromUserId() { return fromUserId; }
    public String getToUserId()   { return toUserId; }
    public double getAmount()     { return amount; }
    public Date getTimestamp()    { return timestamp; }
}

// Split strategy interface and implementations

interface SplitStrategy {
    Map<String, Double> calculateShares(double amount, List<Participant> participants);
}

class EqualSplit implements SplitStrategy {
    @Override
    public Map<String, Double> calculateShares(double amount, List<Participant> participants) {
        double share = amount / participants.size();
        Map<String, Double> shares = new LinkedHashMap<>();
        for (Participant p : participants) {
            shares.put(p.getUserId(), share);
        }
        return shares;
    }
}

class ExactSplit implements SplitStrategy {
    @Override
    public Map<String, Double> calculateShares(double amount, List<Participant> participants) {
        double total = participants.stream()
                        .mapToDouble(Participant::getExactAmount).sum();
        if (Math.abs(total - amount) > 0.01) {
            throw new IllegalArgumentException("Exact amounts must sum to total: " + amount);
        }
        Map<String, Double> shares = new LinkedHashMap<>();
        for (Participant p : participants) {
            shares.put(p.getUserId(), p.getExactAmount());
        }
        return shares;
    }
}

class PercentageSplit implements SplitStrategy {
    @Override
    public Map<String, Double> calculateShares(double amount, List<Participant> participants) {
        double totalPct = participants.stream()
                          .mapToDouble(Participant::getPercentage).sum();
        if (Math.abs(totalPct - 100) > 0.01) {
            throw new IllegalArgumentException("Percentages must sum to 100");
        }
        Map<String, Double> shares = new LinkedHashMap<>();
        for (Participant p : participants) {
            shares.put(p.getUserId(), amount * p.getPercentage() / 100);
        }
        return shares;
    }
}
