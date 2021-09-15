package de.samply.share.broker.utils.db;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import de.samply.share.broker.model.db.tables.pojos.Reply;
import java.util.Arrays;
import java.util.List;
import org.easymock.EasyMock;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReplyUtilTest {

  private final Reply reply1 = reply(1);
  private final Reply reply2 = reply(2);
  private final Reply reply3 = reply(3);

  private DonorCountExtractor countExtractor;

  @BeforeEach
  void setUp() {
    countExtractor = EasyMock.createNiceMock(DonorCountExtractor.class);

    EasyMock.expect(countExtractor.extractDonorCount(reply1)).andStubReturn(1);
    EasyMock.expect(countExtractor.extractDonorCount(reply2)).andStubReturn(2);
    EasyMock.expect(countExtractor.extractDonorCount(reply3)).andStubReturn(3);

    EasyMock.replay(countExtractor);
  }

  @Test
  void testGetReplyforInquriy_simpleCase() {
    ReplyUtil replyUtil = new ReplyUtilMock(countExtractor, reply1, reply2, reply3);
    List<Reply> result = replyUtil.getReplyforInquriy(0);

    assertOrder(result, reply3, reply2, reply1);
  }

  @Test
  void testGetReplyforInquriy_permutatedOrder() {
    ReplyUtil replyUtil = new ReplyUtilMock(countExtractor, reply2, reply3, reply1);
    List<Reply> result = replyUtil.getReplyforInquriy(0);

    assertOrder(result, reply3, reply2, reply1);
  }

  private void assertOrder(List<Reply> result, Reply... expectedResults) {
    List<Reply> expectedOrder = Arrays.asList(expectedResults);
    assertThat(result, is(expectedOrder));
  }


  @NotNull
  private Reply reply(int i) {
    return new Reply(i, null, null, null, null);
  }
}
