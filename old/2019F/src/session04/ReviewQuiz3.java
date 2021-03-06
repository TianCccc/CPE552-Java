public class ReviewQuiz3 {

}

class Vector2D {
	private int x,y;
	
  public Vector2D(int xi, int yi) {
		x = xi; y = yi;
	}

	public Vector2D sub(Vector2D b) {
		return new Vector2D(x - b.x , y - b.y);
	}

	public String toString() {
		return "[" + x + "," + y + "]";
	}
	
	public static void main(String[] args) {
		Vector2D a = new Vector2D(3,2);
		Vector2D b = new Vector2D(5,1);
		Vector2D c = a.sub(b);
		System.out.println(c); // Vector2D@1257AFE4
	}
}
