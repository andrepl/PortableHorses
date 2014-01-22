import com.norcode.bukkit.portablehorses.PortableHorses;
import org.junit.Assert;
import org.junit.Test;

import java.text.MessageFormat;

public class TestTimeencode {


	@Test
	public void testThem() {
		long now = System.currentTimeMillis();
		Assert.assertEquals((now/1000) * 1000, PortableHorses.decodeTimestamp(PortableHorses.encodeTimestamp(now)));
	}

	@Test
	public void formatTest() {
		String[] args1 = new String[] {};
		String[] args2 = new String[] {"test", "this"};
		System.out.println(new MessageFormat("this is a test").format(args1));
		System.out.println(new MessageFormat("this is a test... {0} {1}").format(args2));
	}
}
