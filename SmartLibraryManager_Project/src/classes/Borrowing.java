
package classes;

import java.time.LocalDate;

public class Borrowing {
    private int id;
    private int bookId;
    private int memberId;
    private LocalDate borrowDate;
    private LocalDate returnDate; 

    public Borrowing() {}
    public Borrowing(int id, int bookId, int memberId, LocalDate borrowDate, LocalDate returnDate) {
        this.id = id; this.bookId = bookId; this.memberId = memberId;
        this.borrowDate = borrowDate; this.returnDate = returnDate;
    }

    public int getId() { return id; }
    public void setId(int v) { this.id = v; }

    public int getBookId() { return bookId; }
    public void setBookId(int v) { this.bookId = v; }

    public int getMemberId() { return memberId; }
    public void setMemberId(int v) { this.memberId = v; }

    public LocalDate getBorrowDate() { return borrowDate; }
    public void setBorrowDate(LocalDate v) { this.borrowDate = v; }

    public LocalDate getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDate v) { this.returnDate = v; }

    public boolean isReturned() { return returnDate != null; }
}
