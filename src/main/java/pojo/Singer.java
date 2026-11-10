package pojo;

public class Singer {
    private String id;
    private String name;
    private String avatar;
    private String intro;
    private String gender;
    private String nationality;
    private int state;

    public Singer() {
    }

    public Singer(String id, String name, String avatar, String intro, String gender, String nationality, int state) {
        this.id = id;
        this.name = name;
        this.avatar = avatar;
        this.intro = intro;
        this.gender = gender;
        this.nationality = nationality;
        this.state = state;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getIntro() {
        return intro;
    }

    public void setIntro(String intro) {
        this.intro = intro;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public int getStatus() {
        return state;
    }

    public void setStatus(int state) {
        this.state = state;
    }

}
