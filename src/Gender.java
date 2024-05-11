public enum Gender {
    MALE, FEMALE;

    @Override
    public String toString() {
        switch (this) {
        case MALE:
            return "Male";
        case FEMALE:
            return "Female";
        }
        return null;
    }
}
