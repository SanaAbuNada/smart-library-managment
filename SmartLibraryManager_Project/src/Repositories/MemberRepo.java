package Repositories;

import entities.Member;
import util.JpaUtil;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import java.util.List;

public class MemberRepo{

    public Member add(Member m) {
        EntityManager em = JpaUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.persist(m);
            tx.commit();
            return m;
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback();
            throw ex;
        } finally { em.close(); }
    }

    public Member update(Member m) {
        EntityManager em = JpaUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Member merged = em.merge(m);
            tx.commit();
            return merged;
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
            Member found = em.find(Member.class, id);
            if (found != null) em.remove(found);
            tx.commit();
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback();
            throw ex;
        } finally { em.close(); }
    }

    public Member findById(int id) {
        EntityManager em = JpaUtil.getEntityManager();
        try { return em.find(Member.class, id); }
        finally { em.close(); }
    }

    public List<Member> findAll() {
        EntityManager em = JpaUtil.getEntityManager();
        try {
            return em.createQuery("SELECT m FROM Member m ORDER BY m.id", Member.class)
                     .getResultList();
        } finally { em.close(); }
    }
    
    
     public Member save(Member m) {
        EntityManager em = JpaUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            // غيّري الشرط حسب نوع الـ id في Member (Integer/Long)
            if (m.getId() == null || m.getId() == 0) {
                em.persist(m);
            } else {
                m = em.merge(m);
            }
            tx.commit();
            return m;
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback();
            throw ex;
        } finally { em.close(); }
    }
}
