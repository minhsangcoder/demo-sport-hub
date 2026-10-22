package com.sporthub.payment.provider;

import com.sporthub.common.exception.ConflictException;
import com.sporthub.payment.service.FinancialLedger.MoneyClass;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class MovementAdapters {
    private final Map<String,MovementProvider> providers;
    public MovementAdapters(List<MovementProvider> providers){
        var map=new HashMap<String,MovementProvider>();for(var provider:providers){var c=provider.capabilities();if(map.put(c.provider(),provider)!=null)throw new IllegalStateException("Duplicate movement provider");}
        this.providers=Map.copyOf(map);
    }
    public MovementProvider require(String name,String account,MoneyClass moneyClass){
        var provider=providers.get(name);if(provider==null)throw new ConflictException("No verified movement adapter is configured");var c=provider.capabilities();
        if(!c.account().equals(account)||c.moneyClass()!=moneyClass||moneyClass==MoneyClass.UNKNOWN||moneyClass==MoneyClass.REAL&&!c.liveVerified())throw new ConflictException("Money class/account is outside the verified adapter scope");return provider;
    }
    public List<MovementProvider.Capabilities> capabilities(){return providers.values().stream().map(MovementProvider::capabilities).toList();}
}
