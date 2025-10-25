package tasks;

import entities.Borrowing;
import util.JpaUtil;
import javafx.concurrent.Task;

import javax.persistence.EntityManager;
import java.util.List;

public class LoadHistoryTask extends Task<List<Borrowing>> {
    @Override
    protected List<Borrowing> call() {
        updateMessage("Loading history...");
        EntityManager em = null;
        try {
            em = JpaUtil.getEntityManager();
            List<Borrowing> list = em.createQuery(
                "SELECT b FROM Borrowing b ORDER BY b.borrowDate DESC", Borrowing.class
            ).getResultList();
            updateMessage("Loaded " + list.size() + " records.");
            return list;
        } finally {
            if (em != null && em.isOpen()) em.close();
        }
    }
}
