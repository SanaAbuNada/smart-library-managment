
package classes;

public class Member {
    private int id;
    private String name, contact;

    public Member() {}
    public Member(int id, String name, String contact) {
        this.id = id; this.name = name; this.contact = contact;
    }

    public int getId() { return id; }
    public void setId(int v) { this.id = v; }

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }

    public String getContact() { return contact; }
    public void setContact(String v) { this.contact = v; }
}
