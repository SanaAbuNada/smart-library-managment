package entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 120)
    private String author;

    @Column(nullable = false, length = 30)
    private String status = "AVAILABLE";

    // Constructors 
    public Book() {
    }

    public Book(String title, String author) {
        this.title = title;
        this.author = author;
        this.status = "AVAILABLE";
    }

    // Getters & Setters 
    public Integer getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

   public String getStatus() { return status; }
public void setStatus(String status) { this.status = status; }


    @Override
    public String toString() {
        return title + " — " + author;
    }
}
