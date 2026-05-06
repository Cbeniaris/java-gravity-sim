import com.gravitysim.core.Body;
import com.gravitysim.core.Vector2D;
import com.gravitysim.tree.BoundingBox;
import com.gravitysim.tree.QuadTreeNode;

public class TestMain {
	public static void main(String[] args) {
		
		//Vector Class testing
		System.out.println("Begin: vector class test");
		Vector2D va = new Vector2D(3,4);
		Vector2D vb = new Vector2D(1,0);
		
		System.out.println(va.magnitude()); //should read 5.0
		System.out.println(va.normalize()); //should read (0.600, 0.800)
		System.out.println(va.add(vb)); // should read (4.000, 4.000)
		System.out.println(va.distanceTo(vb)); // should read ~4.472
		System.out.println();
		
		//Body Class Testing
		System.out.println("Begin: body class test");
		Body ba = new Body(
				new Vector2D(0,0),
				new Vector2D(1,0),
				1.0,
				1.0
		);
		
		ba.acceleration = new Vector2D(0, -9.8);
		ba.halfKick(0.1);
		System.out.println(ba.velocity); // Should read (1.000, -0.490)
		
		ba.position = ba.position.add(ba.velocity.scale(0.1));
		System.out.println(ba.position); // should read (0.100, -0.049
		System.out.println(); // should read (0.100, -0.049
		
		//BoundingBox testing
		System.out.println("Begin: BoundingBox class test");
		BoundingBox box = new BoundingBox(0, 0, 100);

		// containment
		System.out.println(box.contains(new Vector2D(50, 50)));    // true  — inside
		System.out.println(box.contains(new Vector2D(100, 0)));    // false — on upper edge, excluded
		System.out.println(box.contains(new Vector2D(-100, 0)));   // true  — on lower edge, included
		System.out.println(box.contains(new Vector2D(99, -99)));   // true  — just inside
		System.out.println(box.contains(new Vector2D(-101, 0)));   // false — outside

		// subdivision
		BoundingBox[] children = box.subdivide();
		System.out.println(children[0]); // NW: center=(-50, 50)  half=50
		System.out.println(children[1]); // NE: center=( 50, 50)  half=50
		System.out.println(children[2]); // SW: center=(-50,-50)  half=50
		System.out.println(children[3]); // SE: center=( 50,-50)  half=50

		// verify children tile perfectly — no gaps, no overlaps
		// NW should contain top-left quadrant
		System.out.println(children[0].contains(new Vector2D(-25, 25)));  // true
		System.out.println(children[0].contains(new Vector2D( 25, 25)));  // false — belongs to N
		System.out.println();
		
		//QuadTree Class Testing
		System.out.println("Begin: QuadTreeNode class test");
		BoundingBox root = new BoundingBox(0, 0, 500);
		QuadTreeNode node = new QuadTreeNode(root);

		Body a = new Body(new Vector2D(-100,  100), Vector2D.zero(), 1e10, 5);
		Body b = new Body(new Vector2D( 100, -100), Vector2D.zero(), 1e10, 5);
		Body c = new Body(new Vector2D(   0,    0), Vector2D.zero(), 1e10, 5);

		node.insert(a);
		System.out.println(node.isLeaf());      // true  — one body
		node.insert(b);
		System.out.println(node.isInternal());  // true  — subdivided
		node.insert(c);

		System.out.println(node.totalMass);     // 3e10
		System.out.println(node.centerOfMass); // (0.000, 0.000) — symmetric arrangement

		Vector2D force = node.calculateForce(a, 0.5, 6.674e-11, 0.1);
		System.out.println(force);             // non-zero vector pulling a toward center
	}
}
