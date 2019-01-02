public class Vehicle {
	int id;
	int manufacturerId;
	String model;
	int year;
	float rating;

	public Vehicle(int id) {
		this.id = id;
	}

	public Vehicle(int id, int manufacturerId, String model, int year) {
		this.id = id;
		this.manufacturerId = manufacturerId;
		this.model = model;
		this.year = year;
	}

	void setRating(float rating) {
		this.rating = rating;
	}

	@Override
	public String toString() {
		return "Vehicle (id=" + id + ", manufacturerId=" + manufacturerId + ", model=" + model + ", year=" + year
				+ ", rating=" + rating;
	}
}
