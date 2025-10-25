package tasks;

import entities.Borrowing;
import util.JpaUtil;
import javafx.concurrent.Task;

import javax.persistence.EntityManager;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class GenerateRemindersTask extends Task<String> {
    @Override
    protected String call() {
        updateMessage("Generating reminders...");
        EntityManager em = null;
        try {
            em = JpaUtil.getEntityManager();

            LocalDate today = LocalDate.now();
            // dueDate = borrowDate + 14
            // due within 48h  => borrowDate ∈ [today-14, today-12]
            LocalDate from = today.minusDays(14);
            LocalDate to   = today.minusDays(12);

            List<Borrowing> dueSoon = em.createQuery(
                    "SELECT br FROM Borrowing br " +
                    "WHERE br.returnDate IS NULL AND br.borrowDate BETWEEN :from AND :to " +
                    "ORDER BY br.member.name ASC, br.borrowDate ASC",
                    Borrowing.class)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();

            if (isCancelled()) return "Canceled.\n";
            if (dueSoon.isEmpty()) return "No upcoming due items.\n";

            // group by member
            Map<String, List<Borrowing>> byMember = dueSoon.stream()
                    .collect(Collectors.groupingBy(
                            br -> br.getMember().getName(),
                            TreeMap::new, Collectors.toList()));

            StringBuilder out = new StringBuilder();
            int i = 0, total = byMember.values().stream().mapToInt(List::size).sum();
            for (var entry : byMember.entrySet()) {
                out.append("Member: ").append(entry.getKey()).append("\n");
                for (Borrowing br : entry.getValue()) {
                    if (isCancelled()) return "Canceled.\n";
                    i++; updateProgress(i, Math.max(total, 1));
                    updateMessage("Processing " + i + "/" + total);

                    LocalDate due = br.getBorrowDate() == null
                            ? null
                            : br.getBorrowDate().plusDays(14);
                    out.append("  • ")
                       .append(br.getBook().getTitle())
                       .append(" – Due: ").append(due)
                       .append("\n");
                }
                out.append("\n");
            }
            updateMessage("Done.");
            return out.toString();

        } finally {
            if (em != null && em.isOpen()) em.close();
        }
    }
}
