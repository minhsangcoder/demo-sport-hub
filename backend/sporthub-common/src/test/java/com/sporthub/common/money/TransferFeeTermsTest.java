package com.sporthub.common.money;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class TransferFeeTermsTest {
    @Test void newQuotesUseTheExplicitOwnerPolicyAndRoundOnlyTheFee(){
        var rounded=TransferFeeTerms.quote(new BigDecimal("25"),5000,7);
        assertThat(rounded.quotedFeeAmount()).isEqualByComparingTo("13");
        assertThat(rounded.quotedSellerNetAmount()).isEqualByComparingTo("12");
        assertThat(rounded.policyVersion()).isEqualTo(7);
        for(int rate:new int[]{0,500,1000,5000,10000}){
            var q=TransferFeeTerms.quote(new BigDecimal("100000"),rate,2);
            assertThat(q.quotedFeeAmount()).isEqualByComparingTo(BigDecimal.valueOf(rate*10L));
            assertThat(q.quotedFeeAmount().add(q.quotedSellerNetAmount())).isEqualByComparingTo("100000");
        }
        assertThatThrownBy(()->TransferFeeTerms.quote(BigDecimal.ONE,-1,1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->TransferFeeTerms.quote(BigDecimal.ONE,10001,1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->TransferFeeTerms.quote(BigDecimal.ONE,0,0)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void wholeVndFeeIsRoundedOnceAndBuyerOnlyPaysTheQuotedPrice(){
        for(String[] sample:new String[][]{{"1","0","1"},{"24","0","24"},{"25","1","24"},{"100000","2000","98000"},{"100001","2000","98001"},{"9999999999","200000000","9799999999"}}){
            var q=TransferFeeTerms.quote(new BigDecimal(sample[0]));assertThat(q.quotedFeeAmount()).isEqualByComparingTo(sample[1]);assertThat(q.quotedSellerNetAmount()).isEqualByComparingTo(sample[2]);
            assertThat(q.quotedFeeAmount().add(q.quotedSellerNetAmount())).isEqualByComparingTo(q.amount());assertThat(q.feeBps()).isEqualTo(200);
        }
    }
    @Test void unsupportedOrOutOfStorageRangeMoneyIsRejectedWithoutRounding(){
        for(String amount:new String[]{"0","-1","1.25","10000000000"})assertThatThrownBy(()->TransferFeeTerms.quote(new BigDecimal(amount))).isInstanceOf(IllegalArgumentException.class);
    }
}
