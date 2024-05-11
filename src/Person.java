public class Person {
    public String firstName, middleName, lastName, nickname, maidenName, notes;
    private Gender gender;

    public Person(Gender gender) {
        this.gender = gender;
        this.firstName = this.middleName = this.lastName = this.nickname = this.maidenName = this.notes = null;
    }

    public Gender getGender() {
        return this.gender;
    }

    public String getPersonalName() {
        if (this.nickname != null) {
            return this.nickname;
        } else if (this.firstName != null) {
            if (this.middleName != null) {
                return this.firstName + " " + this.middleName;
            }
            return this.firstName;
        }
        return null;
    }

    public boolean matches(String query) {
        String name = this.firstName + " " + this.middleName + " " + this.nickname + " " + this.maidenName + " " + this.lastName;
        name = name.toLowerCase();
        String[] tokens = query.toLowerCase().trim().split("\\s+");
        for (String t : tokens) {
            if (!name.contains(t)) {
                return false;
            }
        }
        return true;
    }
}
