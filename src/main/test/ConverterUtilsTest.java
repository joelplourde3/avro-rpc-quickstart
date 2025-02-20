import bio.ferlab.fhir.converter.exception.BadRequestException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayDeque;
import java.util.Deque;

import static bio.ferlab.fhir.converter.ConverterUtils.navigatePath;
import static org.junit.Assert.assertEquals;

@RunWith(JUnit4.class)
public class ConverterUtilsTest {

    @Test
    public void test_navigatePath() {
        assertEquals("identifier.period.start", navigatePath(getDeque()));
    }

    @Test
    public void test_navigatePath_get_parent() {
        assertEquals("identifier.period", navigatePath(getDeque(), 2));
    }

    @Test
    public void test_navigatePath_get_root() {
        assertEquals("identifier", navigatePath(getDeque(), 1));
    }

    @Test(expected = BadRequestException.class)
    public void test_navigatePath_with_null_path() {
        navigatePath(null);
    }

    @Test(expected = BadRequestException.class)
    public void test_navigatePath_with_null_path_and_zero_depth() {
        navigatePath(null, 0);
    }

    @Test(expected = BadRequestException.class)
    public void test_navigatePath_with_less_than_zero_depth() {
        navigatePath(new ArrayDeque<>(), -1);
    }

    @Test
    public void test_navigatePath_with_empty_path() {
        assertEquals("", navigatePath(new ArrayDeque<>(), 0));
    }

    private Deque<String> getDeque() {
        Deque<String> deque = new ArrayDeque<>();
        deque.addLast("identifier");
        deque.addLast("period");
        deque.addLast("start");
        return deque;
    }
}
