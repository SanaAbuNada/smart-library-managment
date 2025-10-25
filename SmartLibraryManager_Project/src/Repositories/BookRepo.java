package Repositories;

import entities.Book;
import util.JpaUtil;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import java.util.List;

public class BookRepo {

    // إنشاء سجل جديد
    public Book add(Book b) {
        EntityManager em = JpaUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.persist(b);
            tx.commit();
            return b;
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback();
            throw ex;
        } finally { em.close(); }
    }

    // تحديث سجل موجود
    public Book update(Book b) {
        EntityManager em = JpaUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Book merged = em.merge(b);
            tx.commit();
            return merged;
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback();
            throw ex;
        } finally { em.close(); }
    }

    // (اختياري) Save موحّدة
    public Book save(Book b) {
        EntityManager em = JpaUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            if (b.getId() == null || b.getId() == 0) {
                em.persist(b);
            } else {
                b = em.merge(b);
            }
            tx.commit();
            return b;
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback();
            throw ex;
        } finally { em.close(); }
    }

    public void deleteById(int id) {
        EntityManager em = JpaUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Book found = em.find(Book.class, id);
            if (found != null) em.remove(found);
            tx.commit();
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback();
            throw ex;
        } finally { em.close(); }
    }

    public Book findById(int id) {
        EntityManager em = JpaUtil.getEntityManager();
        try { return em.find(Book.class, id); }
        finally { em.close(); }
    }

    public List<Book> findAll() {
        EntityManager em = JpaUtil.getEntityManager();
        try {
            return em.createQuery("SELECT b FROM Book b ORDER BY b.id", Book.class)
                     .getResultList();
        } finally { em.close(); }
    }

    public List<Book> findAvailable() {
        EntityManager em = JpaUtil.getEntityManager();
        try {
            // بما أن status عبارة عن String عندك، هذا الاستعلام صحيح
            return em.createQuery(
                    "SELECT b FROM Book b WHERE b.status = 'AVAILABLE' ORDER BY b.title",
                    Book.class
            ).getResultList();
        } finally { em.close(); }
    }

    // ✅ المطلوب في الورقة: updateStatus(id, status) — الآن باستخدام String
    public int updateStatus(int id, String status) {
        EntityManager em = JpaUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Book b = em.find(Book.class, id);
            if (b == null) {
                tx.commit();     // لم يتم تحديث شيء
                return 0;
            }
            b.setStatus(status);  // تأكدي أن عندك getter/setter للحقل status في Book
            tx.commit();
            return 1;
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback();
            throw ex;
        } finally { em.close(); }
    }
}
