package Repositories;

import entities.Borrowing;
import entities.Member;
import entities.Book;
import util.JpaUtil;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import java.time.LocalDate;
import java.util.List;

public class BorrowingRepo {

    // حفظ إعارة: لو id جديد → persist ، غير هيك → merge
    public Borrowing save(Borrowing br) {
        EntityManager em = JpaUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            if (br.getId() == null || br.getId() == 0) {
                em.persist(br);
            } else {
                br = em.merge(br);
            }
            tx.commit();
            return br;
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback();
            throw ex;
        } finally { em.close(); }
    }

    // CRUD بسيطة (اختياري استخدامها)
    public Borrowing add(Borrowing br) { return save(br); }
    public Borrowing update(Borrowing br) { return save(br); }

    public void deleteById(int id) {
        EntityManager em = JpaUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Borrowing found = em.find(Borrowing.class, id);
            if (found != null) em.remove(found);
            tx.commit();
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback();
            throw ex;
        } finally { em.close(); }
    }

    public Borrowing findById(int id) {
        EntityManager em = JpaUtil.getEntityManager();
        try { return em.find(Borrowing.class, id); }
        finally { em.close(); }
    }

    public List<Borrowing> findAll() {
        EntityManager em = JpaUtil.getEntityManager();
        try {
            return em.createQuery(
                "SELECT br FROM Borrowing br ORDER BY br.borrowDate DESC",
                Borrowing.class
            ).getResultList();
        } finally { em.close(); }
    }

    // المطلوب: Active borrowings in range (returnDate IS NULL و BETWEEN :from AND :to)
    public List<Borrowing> findActiveByRange(LocalDate from, LocalDate to) {
        EntityManager em = JpaUtil.getEntityManager();
        try {
            return em.createQuery(
                "SELECT br FROM Borrowing br " +
                "WHERE br.returnDate IS NULL " +
                "AND br.borrowDate BETWEEN :from AND :to " +
                "ORDER BY br.borrowDate DESC",
                Borrowing.class
            )
            .setParameter("from", from)
            .setParameter("to", to)
            .getResultList();
        } finally { em.close(); }
    }

    // المطلوب: Overdues as of (asOf - 14 يوم)
    public List<Borrowing> findOverdue(LocalDate asOf) {
        LocalDate asOfMinus14 = asOf.minusDays(14);
        EntityManager em = JpaUtil.getEntityManager();
        try {
            return em.createQuery(
                "SELECT br FROM Borrowing br " +
                "WHERE br.returnDate IS NULL " +
                "AND br.borrowDate <= :asOfMinus14 " +
                "ORDER BY br.borrowDate",
                Borrowing.class
            )
            .setParameter("asOfMinus14", asOfMinus14)
            .getResultList();
        } finally { em.close(); }
    }

    // المطلوب: هل الكتاب مُعار الآن؟
    public boolean existsActiveByBook(Book book) {
        EntityManager em = JpaUtil.getEntityManager();
        try {
            Long cnt = em.createQuery(
                "SELECT COUNT(br) FROM Borrowing br " +
                "WHERE br.book = :b AND br.returnDate IS NULL",
                Long.class
            )
            .setParameter("b", book)
            .getSingleResult();
            return cnt != null && cnt > 0;
        } finally { em.close(); }
    }

    // المطلوب: عدد الإعارات النشطة لعضو
    public long countActiveByMember(Member member) {
        EntityManager em = JpaUtil.getEntityManager();
        try {
            Long cnt = em.createQuery(
                "SELECT COUNT(br) FROM Borrowing br " +
                "WHERE br.member = :m AND br.returnDate IS NULL",
                Long.class
            )
            .setParameter("m", member)
            .getSingleResult();
            return cnt == null ? 0L : cnt;
        } finally { em.close(); }
    }

    // المطلوب: إغلاق الإعارة (تعيين returnDate). يرجّع 1 لو اتحدث، 0 لو ما لقى/مغلق.
    public int closeBorrowing(int id, LocalDate returnDate) {
        EntityManager em = JpaUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Borrowing br = em.find(Borrowing.class, id);
            if (br == null || br.getReturnDate() != null) {
                tx.commit();
                return 0;
            }
            br.setReturnDate(returnDate);
            tx.commit();
            return 1;
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback();
            throw ex;
        } finally { em.close(); }
    }
}
