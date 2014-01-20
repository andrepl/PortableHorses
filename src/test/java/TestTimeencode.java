import com.norcode.bukkit.portablehorses.PortableHorses;
import org.junit.Assert;
import org.junit.Test;

public class TestTimeencode {


	@Test
	public void testThem() {
		long now = System.currentTimeMillis();
		Assert.assertEquals((now/1000) * 1000, PortableHorses.decodeTimestamp(PortableHorses.encodeTimestamp(now)));
	}
}
