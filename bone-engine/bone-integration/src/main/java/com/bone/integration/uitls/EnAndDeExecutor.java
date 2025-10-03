package com.bone.integration.uitls;

import com.bone.integration.flow.node.EnAndDeNode;
import com.bone.integration.enums.EnAndDeEnum;

import static com.bone.integration.uitls.RsaUtils.decrypt;
//import static com.bone.lowcode.integration.uitls.SignatureUtils.*;


public class EnAndDeExecutor {
    public static void main(String[] args) throws Exception {
        EnAndDeNode enAndDeNode = new EnAndDeNode();
        enAndDeNode.setEn_decrypt("加密");
        enAndDeNode.setEn_decrypt("解密");
        enAndDeNode.setEncrypt_type("RSA");
        enAndDeNode.setDecrypt_type("RSA");
        enAndDeNode.setEncrypt_type("3DES");
        enAndDeNode.setDecrypt_type("3DES");
        enAndDeNode.setPublic_key(("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAohgwqnV3eXdzf9+MwZ3tts2CuOU09GJRYpe47Bb76oS19ZvEUBFP9kYfREwe6qMlyzdpK844gysFeChHjVnfmNw4mQ+AaQsQZa1/Nioakw+J6v0GkVzuZVAhNiMie8cK25h/Td4ckDUwvSfV+k5LvMIwu7Bd97NZcO5B2PmraAPTN782yHIoM0Ar2CrAZvtvwyaWL+ysgbc4MOKdBxSBYcAfpBpGr1evjtGiQOJNjP6fwlDRH2itsfyWofYRJoIucLUoQrVOh5789nkvnmZli+BdyOj0OIcOOvyB1a5YiKVbcGDoNmG7uRDF3hjFLVNFmC8zjQ3XuvSF8qRfiD7KWQIDAQAB"));
        enAndDeNode.setPrivate_key("MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCiGDCqdXd5d3N/34zBne22zYK45TT0YlFil7jsFvvqhLX1m8RQEU/2Rh9ETB7qoyXLN2krzjiDKwV4KEeNWd+Y3DiZD4BpCxBlrX82KhqTD4nq/QaRXO5lUCE2IyJ7xwrbmH9N3hyQNTC9J9X6Tku8wjC7sF33s1lw7kHY+atoA9M3vzbIcigzQCvYKsBm+2/DJpYv7KyBtzgw4p0HFIFhwB+kGkavV6+O0aJA4k2M/p/CUNEfaK2x/Jah9hEmgi5wtShCtU6Hnvz2eS+eZmWL4F3I6PQ4hw46/IHVrliIpVtwYOg2Ybu5EMXeGMUtU0WYLzONDde69IXypF+IPspZAgMBAAECggEAItovKpOPwQj/R00eg2ns2QuWkiFMUciLBbcoLnEhsNLgmeLoYqvaRNJeei2gzyQNpgRvw1i3m3JmXmfQvLKIimI9dCGaY1ua5rXXfqKubJTge2t0nFA+PP/t6ClFSpZpPf6rzqcVfqdFJ8K3Njbb/4XEezKofbAtKMQ92cSTB43FZKWrk8KI6x7gbFcc0vpKXohGrh15zMkuKnIYhImGSPIcS6OTf3VOaOqK4PD43XBbyxKPPrvzDxBAMJIFZXmrwvkurf1VTr28zNNSIsOK5AUyA/ER26Xxd1+EjnZiCsq0XY3ZLxzRhYgUmXx/ECmKLwew8cYDJ5lPG6VzTets9QKBgQDLf0qIAKhcDNmt00yIxXaLQ1vp64BdEeV1b5JQrffVJlCphdaDaTS+KDqWi08Q5t0JZHMNeQvmssYzzqywr8QnMl+viBki6xCwuvlWo4M4gMXP+QE+AZoGT0rBsPy/83a5Y8Jao2SyvCj4zSvDxNJk+8DiGluKde4QCslZH7Y2BQKBgQDL6k5DjusEhl0gUAsu1O9ejzaNESEhiXHY7RCatdiiilsmiCVkxq/1wJd59TV19+lrP3F453vu+CPGCSKeEuJuZ2XE+Vdsp1eEoIsyxA4ksusHE43XfjtMUuvWVcYX3BSCloFalXRfqYwogpCpAye/MhF02iojoyHs/2m3ais/RQKBgCh/EmPyTY7T99XBwO9O7IJWS0pH1WmwEAvIwkNP+4vtbgvuWHwaSaa19sLSwk9IwiAGX2cE+btVRGA29uLKd6ydN7GE0gvC37Vr4Aa/z3NFHRiAIyElzHMJAIV/JS1F1BCcq6bB8V4oV7b7jYQN/RifiQj12dW6FkAXBW65AtfJAoGAayYqi9tcaRtFcgY9ahVf1ntAee3HT/0OAqPHNtRZCkfTT7L4mzTAH01dCyurNNrZH47NzG8us0pWWehQ9WkM7GCOnGs84g4osbs+iaV1nRka4LQIs3RUKm3kGNMnbUAq0tfNDtE7vky0JlE9PGI5D4F6pMFCpUlCOWuYnFVAdlUCgYEAjJ9A1gYCR3PvMT9m+W1drV6+tkfI/LS65tcreewG8aK5mnNhu8uaakveDeiKHotJ/PP0w362K1NuRoEIbNsetQ+ChUojjby+1s7UcNcWnM8WgmTqZFgMn6B5j1sYUgq3zIlAR/a8Xbd13mLL4otW6xJ1JpSMcDsi95/8HnZOBIc=");
        enAndDeNode.setCountersign("");
        enAndDeNode.setSecret("GXqJrhNwuiZdgK1/8rC/O87pPiNkbrZz");
        enAndDeNode.setSign_code("");
        enAndDeNode.setEncrypt_code("TransInfo.TransBodyInfo.RptorName");
        enAndDeNode.setDecrypt_code("TransInfo.TransBodyInfo.RptorName");

//        String content = "{\"certifyNo\":\"2110180678613416\",\"certifyType\":\"01\",\"personName\":\"苏燕红\"}";

        String content="U/2uxa7S8bPkj38s52bqeT6vyoVNz8m9kBfAzYZbeFOn22nQEvF7RRGB7Q4Jb9u66c36wQsgs6szKZOX2bugYdBpXBObzIAvZbxfH1DmcPctchB43wgwqBqb8Bx+FD1MeSCBFF+flW1+YOX+KOE78h194Q8eFREJQhLda/DzMsT01Bjsh9FNT6kzAZeqa8pGq9iIChlmSkBcjuOUJFhlGdC+fvyh8D8ptDn5QaQZinLf2bCqShKWv72mdkIPGDwCMtaPOVyp/7D7V18+PJQhxPQajBXllF5AuqrT64jsp2vZOYEAI3FH2JB/dEFgfiMw+oN4r4ii3eNKkopWoKtOaw==;Saa0JhI5Ydi7nL2lJfTmmf7OClvHK7aq4RsP+0hoPeHh3R32m2i6hJzEnto2Cx5eCAAQl5qsbJA8usPjAjWzFCp6V1jFrUAUbhySIK049jeOSbSlDiod3KR0nXyGq5qmK1qgZ1ssICdxnQjD9zT3H0fTfV3fpPkPESuVAjGAFzZD62WiWHZMxRAklY8IMM6lDreVe6nIya0TaUbTx1i+N09FZdOTGVCIkk+K5gsPre1zAuba5FIZKl7N+p8bJ8j4lkvYCOMxcwkyq3xGvDH+tyg/RAFj7+rCN3Ra+HvuNCktlTsPVQVBSsQdgx3g8wQtW0x/k30DIX4YJAyuEEnqkg==;X2Cfr0g7ZtkttHYGH0zC4UdKkvUZfPFpHWiKosk0D0TMbQJcc3IAKYretfxGwTFNp07RR33dQiFTyen/yE91l3ssSKK81tEdu7pHBVsEHdXBbsjPHLp7fJ/o0edyClsYacTZwId1/f8z8e5JhoVe3irMrbyKL49Fmj18jF3ZZwuev0H1kGR5Dpdagm+K2WkJ9OYum3NwPf5bmiFbh2oDiSBFrf2x+VCnHUtBf6FrvnvTRYY0ncCJuXDXKilQX/jAyCcR8nTgzPVTNqOBsFjykphPk8FyJOBbGwo0dZkeSzoh24fdAEM07/jMJSSmY46DKSNWEAC8ErRRgH+VjR7Dhg==;IyAOkbVNPHTcOqaBGuSyIpwbVu4FdPdIG1ObB7AUn0bGEMnxQl8/vedCPxzsHfH5ol5Qm/if0dtEOU9pF2/8puj+fZmg3Tehp7nbvWWskG8Bt+ngBCNZV29jBT0hcj7H1Ddw7c8dhiA1VE/CPUXrWSF9nkLx+Vf9u97rEylJX7hLdPjpI9TAF81teMk90DDbx6QaEzI0NSFXy/PRd7TqJG7SvNEKQ5wE5n98e1s5fDBtZnilYQw0ztWNl542mmUDfWX678NLFGMdk4x3gDrW8cFN6TLBdtegZpYZOLwH6nlie2sJTJaeFdqn2A64YpHX0NbSkadRuZYJajIm50i1Wg==;PYC7Tah+rxNgMP/Qnu7blaHiqNn+gW7C9W02Xkh2WKEba/lX6WO46POsjYpujMwQantm2gwLQ104IbqELl8NQCIWVf5aGHdGQlh/0j4ZiGYvWjwEH5MMGu+dgWL7nq6X9MGTr5e1ZIUwKeFhgZEjvC4POxNvPaIx3czxvpMYVVkq2SYboUKqz4MguVDY+7DGorHPvTMBVfJ4a8oHnnCoEqhDhj0zMUUb4lfTcfGaqCJs7PAQqIrv4rr0oV5Fae+/RLJ12AFqlm3t1GX5XPzKWaOzT+qnu3oeotse4A24KUbIr6m1CdDcM9sCsd7kBH4LBL5PgQ2opPNEqNbAv4+WaA==;AYNBFwcxhSaT8u16GsU1LUrPpDdbcVz5dPadoNk6SY+qMOgjKH1hsoTQuoJwhX2AtjWP/U4lqmhFmC/U1nrMSeK/SOrnZh1qm4kSds45Zc2AKZmyhgUofmKE8D0i2N4lDSdaz5Ayj4+F/Sof3SGwy7Dd5+/fzwK+NWlv95eUla5OsV0z7LHnhCxUta9kXz3omVjaIzBmKG+mg0ETSrDHgyGc1knuPyuXGFO15/Vz82gFAu5VDX80CX9JXBg70L6dgg5Lb7K+kQUtt0ihKrv61/09QOBAElFT6si01PJ3ZqyCzsOyM0kYlGxnx21Snh/yKVY0LexcWezEEAr6x6uTbg==;RnONuF+U5IEQjwpkmYERKu4Jc92hRHb/42AWZQyIjlcVFeplbnWcbXy6+avcgWGF+YBSPFWUv7B1WR158PETqv92+rXjenTRpOKuS1Xv/GaYPiwTeGTbgkZQoOS+4k5jJGN0aIQh9LRmw6Q1zA/OdSyfTqQyEb6xJlc+6ajJoNLizBo5y/RwJ7Iz596m71Q1jykPF4NCM87iC5FfZ8HHvURESqIfN8D4oW8rvTbranf7W20xOVEc2JI4E4w4D9X7VuzmuHR7IG+TCkE0hpFnaeJtr7P8gPV1rXYGS1n/ROclKziWGPJP0l4/f4ERr9d1jrSQls0tLiK6KGicFrByGQ==;W2s53UMQPqw41vlIPgAZmvJPszsxdGT2/2m2eKmRG0/N7zKNmUzaQnTtTweBJSBS6+FxVuGC3U5UbNInw9kCRiQyA1NW/NnC8kmbhLwuI3TrftZ1j+nP3QsYjGgdNGd6wkeTC0qa+eljLmrKKeJeMIaXoa4iwmeByxhKYTjvmoqLUFB6W5W54VxJJ0bjWH911A6wkV4YideelIFWACEjCoR2kzMAR1fj9gj2S0zdHuE+/lJv9NwDLyGtqtQ3PsdjYyBisUAHQie6YjwPSZuUWH93hxsJ9ZfqFBEXU63Qw+OO/jtBuB/deQJZ6ohla6LanaoVFU/ftb31OwnCU+jRow==;J0LiyO2pgWSxUJDUz+vRm03FgibhaZJWb4HYIG66NmOuFFBPBx/cziMbkGRAOenWmQTjevm1dQWPHLMp5xpXqRf6xKnXzS9Yl/vhSyN33vpzUXwHlr8D2q4GDnXNx2i1P9znfta9aNDWcjrN+OsnDXncictbYp6R+eu5WcoV9RD4KnDAB3MyYEyLBUgzRjFJQd8Urjv3Srh9+EiFSaRklqnI6fOMGkYbfzXGFjsYrkamOyBim8G6rFPwiTmcRiAxg1ejgWf+PZ9m7OIP9AXQjPdNpMAp1VrePL729XTzXEd/IFwv8VfTyM8nwlf67k+CVM3moqIrp/0lMqTfckwy4A==;ckrBGVdq37ovqPXHTcYtELWekgcGUTifmzFNaBfNpuftsVDgnr2m+08mP5xCqPho3nH57b4mLwNnu31ldrbC5f10QGf8dIGu9B2xa3+9l+H1HswCurCY35CaJCnJKuHiqut8bK/rKW9flGidGDe8CNRgQZYzhuxbIik0wTC6TWMcWwicumsK6bYvZNFW8ySU5BAyjw992O3F6hpV/j0Nei9/yfRTGiouHeOz1cXAKP1ICsQgk1xM6oQgJS4RZJ/buEGzWdbYiMpdXBbYs7bQyaYAIHSFiB4fnYb5bRzBCWoPAiM1kh9SrGSVbkbEGx0UVQEGun2Vjc5mDy2Yv5Zs6w==;htsiu8t+tATcwauhC5J4ZNVMyGtHaYGXLAgGHlwzE9xhC2t+zjeOGSRuQ4+YOpgkygvm8eX0K4qldne8yVJJJRyGf8NCitIGY4dls8JwbRBTabmq2DTI9DbLGH2YBCP/3x80VJyAINNFqSOS3e7U+A6S0xUUF+kRdCYshbFI+M1B7wTNya8/Jiz3QElsCmtwz6tJC2TWJ1u4Bsug0S45B5jqLBjYr8lYLte0qz9+82SP/hglyCmnZIu7QXaFCDcJ3RW/h3s82bbGenM4shFjfX5a4GWffxXYc0aBNJkc3loBRhSADYnUs9apO/u8PvZlPr+dRdqVMeTKD6qO4msXUA==;oGwvabJ4LRt0JSCuZP0nW9q3UAodeC0BKhrqCcffsqbmQR2kZXZbQLAOVwVCAQNfbdNcat6csSYLZkz7AAMDzf7uZ9kJCrSX2jx3hs4DlIwatnNamKTSHMiQwNEvfI3PhVz4X4N/IMyG09qgZngEbIzfBQ888Iw7qy75NVDRmdsAjvTqj+LKWqYHdCJOZp7p7mh11Evz494tryNOcJpaCaFGZCqECTpAtZ1xsJqcdJq2SRJOioawRf4mzo+5f/xXnAbP3WXw9IMmw9U6TDd/fvbvzLD1OvLfiqpAlNVYibreBPn9dlgeZg1SznkSkCmfbF9/rjxFoumQhLUE0RzdiA==;MTLeb5Dxa2VtgFz3wysD7brehYbvIeaLlErL7xQ/Q6odXXlbpsTCBkNcHJOAHBPCXFu4xyiw9NaRRblqJZmBxcHP2xJb9jshtQR6I3Ipw/JKPO8jyMWZB9608V+oSrDc8NmrFNcSXcTSIDDAOFxtlcyw08k6mjx4xiRO68JCHGFmqM0QsFcU9oeSoDKg91ANfg8IJDqnx0J95pMhd6iAQQ1EN13+U9QtPPOLneTT2tvakhTyTm1/pvhVWcSv8gn7PmbLZcrTxBizRjq5nsWQ+wUHApzt/ntu/L9PNFVPVecN66CeyUN/3dpv7+58ooLHsVXZDfAloA4PCXUx5FEV3Q==;DDyKLR7WE7dNmJlTk2XdmvRoUG/C+hoshYcjY4gKeCNAOhAWE35ZcaEXqa9aS8yVsQQFoalOPqv2cGKPjfT6JDH4avya8jtFJ/XG3kyOfvfho3pCBgygqhbt1muJowiY12fqWDB1XiOaIBxQ6pLssRX+IFqbUFwxmxo8JrwllpMTz4sKKLY6GMXsVUPe2Y9LUNBYnONg8ZYpBPu6fj8+Z6yKciDM3j2+oNFZzHG5tnJEEGVVA5DPg11wofbKkIF0kRxqXX/BsrK/8nIpBALw9VE1+xRRq7DWBCRraC3vPjXIvyjRboTQYYrOUansNdWhLW4hyzexlQxV0BB8JSNGxg==;IbTFFmVs5Mx8NSGV+RQuwVitPrQyzGM8UDJ+fAcI48YOUN/U7O9lJ3rGfPFUqIR4Bm17I85qQ3gNjxu8XSkQz5hksKf3p1jINrIGkL/tPK3MTWwDpPA69rq+hq122Q2Q4Q+3jG32WAwiLiy18nzdPGG5Epp3c0y26atGXPtLWaJujbZlgM34Vke3qDkedDy8yqb//pJDyb9V386s7v6/170iKo1oljeDVO43Q2FubN8POVtdaMV0OfFoNJry+zdNeOqpNP/Bz1+k5JSGgPuXYAc57MIra92rRSuwVPmw7lCC8AZdmJ9maiUwu0dgBE/BmGffAf5IOPqwUwgDZpRa1A==;MBdUO4Vq19CZ/CFgMwBdQ5OGuNatmxxDupJJptxvSS+VtZsLdhAMYXcBEyWusbAxZdvt4CxkTiP3mcO1L434Dh8f3bvlMWW3xUuasif38o2z2n2fh92Wnw7KfH1ZYYQXE8yUMLujuKFqkNlPhDlm29OvkRkb4zrmAxAoZy7xO6XKLcO8EOvNF2QrUpixW1pXTyli+jqVxlcM70d6OJ84hc30bsv4SeUo6FZWi9emZX4sKiQDM35fzSi5dqn+2BxoVZcAoXn84HcGFeJtFpZ90QeGT7yVsDPJcdhqHQc/lhVOlUwdlb+BQw7TLRi8tpwIDB1WZ+9Ngnitp2h5H+Imig==;Xz+0Z7N39ArqYI5ilqQI6bURJ13QFxp2CYGQeVHIweiJUaOLJ659162jXxrKTVucs2GXfCmfVFfrLxV8tI+uuhU4uQs2U0pz/GG505usjAIDIzp9aDe1IGbwglhfh/B/I/+vzihq6PFCkavtk6cPzkJyAy8lE8PTVqvJP5zHNs8rYhHHNvn005Zg8gdlH3tjKRs4H8iBbjw2yGNNvuSGb5SSmprJ9SETjo9cRoltMqfsB2OEFvh/5noXqMHvWREXY6N3JDuL+DLu53vDmfWh8I1CxiA9AY/ZYIYD8FG+vyMANn1/gpqHoLPo6SMrqr/uxhARH7MVO/9KsladFwAVxg==;QgF/FnMOQ9sKB0ARFBPVDjHXs+rojYZ91NK6MPnnIt8GXvF8yUPnGhBzcPpCfj3XP4CpygYJzY8VJUZoInGjTKKRaog2dhsL8oCawdJFQj3axTLEZiS0pW3wyY6vOlF8Io917EDDQKhwVxItG4kJhA6iuiy1XEzq3+uTkSFg4q1+RPyT49T/CJs7b5jaaqlbl4WSjfh/FVM54Gv2D2GfA6HVuuIPPBajegyNk1zE/HpXlO9ImrGB4PwFKrTOZFqZxnKV6C9f6S1ug/VVTU/n3hDuP8nW2JOfJFuNonoichUs7AnoTj8NdKhgq1vLJqXzeI635M9kHLkW0vJ1eKexDA==;TxFMSdevrK4J6S0+iBhnjjYOxhHdLdd2fdlnynGPH0oULVyQwTv46Wo1+d2fsVydoBLne7SJj/m7DYqlNcAxnSQlFuF80rjjKg1hFMNRK9hNDsXfqle0xpuJPz3M/s66oNPF061DiJzCAX0Q2OKeo1AmQXq+FOBIDWCto3/Aglb6LEp/inorFIBfCa/Hwl0oSoMYBoqpRanUy8lX8/lV+qbmLHZbsPD/gGNWrcgzdznn76+jyDa6Kb6Jv4J0bO/4idQBYid+mHPziu/M2ds0wPNKXefP+JWFJ4ho20LZt9zmq6Npt3BEvLyrSul/wemDQ59hY9RMWV/GY5xtN4r6vQ==;PAwgxKVsknGGAL4CGckZ6PiuDQdZtYMM8tHeCO0PBq2BNxJTwCjyMzfUQhY65JCdZpuJSjyTwQoRHUEXsUStMUiDfEooA3tReQJOtQ8Z2ytg867KXgOFUyrjVxVzLmWa1LFZziFPecGdIm9ZZQ7WiYVE+1WuITq8PmRBtuOlHL+6Pq0raXMBFiUf5lF2QAHIDkilJmwvdSSv2IxCccuEUae5fB5TICTNvnHzP4aNJzX0sRWN/TtkE6HqWo3/UgsyM4fcSNxT8sHwstdAkFrdoUNcSjnBqkuCzBb6Bzei0UiF8+P3YgTGpK8j/45UgQ6ku3Q4cj2l3JkTgXI8YwiqXA==;cDKBExUUZicz7rA31ubkeW2MmGCrSqjqvrKaQPBJ6AsF1j1Sn7hb88mrh3lBg+wFMf/xmS3bXrh3ofq+oCpQjpFC2PMprpFXByOt/1ZLnc/VvfRF/0Cq+l3k9DUfwSUglN8Sq08pb9/eFqLZx0D6ToRJ+MUDZWw0cuPYArcW1xIpdpty8ZNDXhoPOWorx5tL80k7bQGWCgh8I8kdWvUPFcqdNpcThRF2PKRZSm1LfoyW85b2uzYpTnSjSEvtgK3cKNYuNFoFCyUThUPtj/syT7B40AqCMfMSme+Y26OVWU9dC0FJp4i7MJ86sbyQ740NETa4gNcx48LiQnFey74SNQ==;ZORqhjQWPRdzN1WuRjzmSf3bDp5FJAF+KZIvNB4xwsBbgMNI94tUTCjcVIOeDbtpqdLYCawib7wrM3dEzPQlje/HDkakPD5FjSc1YIvel2IZcdfVOLl93ul71mE6mQaI4uSGSCq9ArKgoDeeo242gFezIuEVq+65sDyUjaXvWwVmYM93xMmzNSCzADtSh7VFlmvG7G1AJK0vzgigep51f7tGwPwmV8vOfItFOF+7Bnn3GUBGRJ2TMH+lHCVWCPoYOVpBZ0JWlOiebY5zv3N0kp6/ON7o00dQxCpV+6qYNcsXXihu7tCaQWiQagSbUM7fQQCkB4LeW6Y8LSQ9T/1s5g==;mpRMIjYCoN5xnBdZfNbT1VBsSkhTdHEIvXEhdBLv7A0iJT+RjYUVc57J4j1FT9f8MAgw+wufpP3Cd5fMJ8wn9Ghi7pZA9A20D8Q/+/YHzn8pE1kmr8nRTiTxCQjkTleYeTCztFUe9nyVO/G0OEGn3YSparQxkfyae/ageZmSystF1W06i6s/7GFshWL+S1HdQq3Nn9dtzQBYEqVQpWxJYtHnkYnSxxoaSg/ZtQrJTvq7RFfiXqwO6eR9rggHOsuINULr32utqP2Eo3NdcvAbPPgmk3UaEcjalBGMLfhCLki4WcIVI5c/JL3x8lWEEprwXfO2y8i74+Ntz436mNzOlw==;i7MtfbKyCLfd9zgp21UBo+MmDa5IPQr+o8PdXw5YkUCWbOU3Qz3Bm1Ex2SNpzOH3BaimsXu2I2M8HzsLPi3pS1DHdYJ4KM5c9QEX4It5Ilyrn1HdoVWnm46fkUMywjr86gjaVvEGkR9t+5/27wWBhV61UV725JMb9luchXyOSlPXOXApdJIbhlm1fP+eKWr4jLu4UeXoamA6bb8tkiVcDpz7vdfcuKdjsOVTBvYSgJF4jhkkrY3SLgjz+6nCSOKmxEng6s+5KiFnR1A84nnGMQLJ1VxFI7caaskN5cvqBXIOD/Pnl0eBGuHJ6X92n4VRgRdX1Swh0YZ9kH251ZhPxw==;MUt7jyT7u5nJ7DAfa3WCCOUgYvlb2Jtms5Z/vKkjh94DQrC4L8KyCPLxusPZi6Y8mvlod92vkrXbboDw1hUhaS+4ZWHMjU8qNe99kT8W72aVoqprOQYyoi9wFkysvxzQroI4hsREMPH4EDDTdbXyogxu8E/AJkuak4q4eOEvOPn7EiMl4zkYQz3kIWko+BSz2RRXgPuuhTAbM0PmLx5WJ5MTIxSlS4q9druTS6gjVIAI0WHQZ+GxT5TjuTADjURqDT4zfoQ91cKl8Zw1s/kjaZfbst44kcRg5qva6sgG2Q/O+b5ngoWatQUYEwb2+rtPkwIjhijgnrtfquY72h93Gg==;fS2xFk0e3jWT6tBSo5S4HKO1hKUFimkKq2wBsv0Iiw1NWk1mDzouOtPgusS/rrB+fJ2zTBCOdM0O1cwV8zzSI5aF7S1dRO4OKCMVL8fwVPXYqD9SZBDm+QwGl7r4Ttw8ol7zNjBZpXTV2Bqrwd4lW6B9KMMVfAW2gilVGeJaS1M4P12AM4I/oUuGpxVY4Rz59cUGrB3RBTcrIupkbx8ylI3fYPWAk3R3o4VDps34CNIAoZufn73FTI1fYrRJJN9gIlWUVNE+gH+mI8UBUaAx4C/7bU+8eNEwbn5n5+jyAauwKRV/nmhHBtlbyt0CxHZ+o89zNflzRZogAgUHUveiAg==;cWhdmNOqc9Bv4JNyfom0gsDWs2Dej9D3e5NdnQ1LNk9u/rcxfDHO+1DQxseyV1goAFKJhUVwZbxiJS1FmV7QQS70QoLYdEkHx6RHN6nNAmFJnw/ugIMKslJN9a84XPJb8NnLagGQ3D0JGlnNz4UxdXwfqaVf5IbIDQzQUnNUmGluXSp4DsbvDLqf27gioe4msUzvuRnETtyx6VFchgwO/MDDt+7nY0umXVxcSbTsMjvYeCjkxrSxKbfNyfDh/l4FFoXQMX/RYj0Oi+QZfnssnRciCWFx3cl8yt5SOhzP4eCqSxa8VOtneQrJnUfd/T1AGbAwGc1C0jrwYYxYnkD5HA==;aMM01cSu8+G9KoKJbKQ7s4dNaaUj5lOOrzXw8UA2xwUOoaT/pyvfPB2Eem4kzhaemeWJUT2VH8RuBYaLIi9lCF3zaSqhv5SUQylT/BNCrzTiOoKVC93o8DUaQGsEfsMru+WlfqzzE9WJve2E9hVFEJqDGaK03CvIeXviO4KEVGpdXhm+vrPXi8TMXFJ8Zzfpk8IvYYhq+HUCYZMJkgq5EJ8+IFQOG1LWsHGThd9zQDQaKSV0S7cmakHUsk5/W6cXJTr9kQV1Y0dSuIbr8Gao9LJ9n27Ex75zJysHUo8UZp8Rnj+p7iW9uJzL0vcaYqOHFZfnQMzAFH0IuK9nYv08pg==;D8qhr/CinGwxe9g2124ur9wxe7BMBLVvXEfJsMfbPm7YaJ+ZGjpi1Bq4XCgXGtfq+WKFpcM8lzpWGyuqeTGH2fKnG0zexevi/NMgXb7xmBgH2GTE1r0hFHh2Wx0VUQOtQx9thuOtqWLWsFYFpz+wui5YjTfvu/k/s3o+lTJrcwrQKoDgGRVMYI29VyL4bYKH+bMcnXXs2XwfGBevxZ2sjp54LLp+0EeAXUU+A8EBvXz7X3lIM6dQVeoVjw0YjbYOQc+5kiD8B4Z+etwM5MrIvaLTOCF93xRwRCmBtDxvJqyB52VmrURZNB8tWLEVmRCby5eIW0mDE4dnS8iPB4eohA==;Jr5LARVEyLl+dYLpmWtNzKHvrAGh5SlFqTKd8a4G5erSq6lW2iZTuWl+4q4Xg5Tx9lEB7xZ2re8zTsPsQDiSeOxSIVUMljvL9ihIIF88DtMhDJD1VaQ5urTA9kMYoI6KWMHqOCZe1TTN7M+IP043mral9cR7WV/mCVR4fDnd7XsRDcRlLiZIZA1RBzKLnLpN3ghBTgmLUm/pqQKycZ1aXdjkufQuGD0ObABYOPMM8RYWRH+KWJ1Lyb0b9iLovY3P+Nr+7d/9wKTfPM0VYxS43hcQHHN9nmCwfpHyv1vrDtrXXMC90GP2API5Ez4AuHYEEoP8KWvCM5K2vIrfSP2ogw==;NSIvPPRy+xq7VqGbMKTSDMulHrnmVPYl80P6HJ4TutxheyWqkXsG2xAWFWIBy4qy9u8l4W0MfnbjrEmmeXXQ0K5MeMW+LmL2CPDJv96z2aVvZMmI8n06Fl5JF+5xwSB+JqN7ivtM4bQPKEFC7Eq44CqKBgMEI/1cEA1nHAyM/78NGx00xxIhd4iIAB7hrCU1eCEqDQZV2F/uCGWCUe4jA8qNo9z+KAdMMa//TFUnWfjsjS4rWL3k7tVUwn0vgkQXeB4hqEsDOfTnO1j1RBnIECGADIbAAJAWh6LY+atcS8/CM4GEAi0IydX+nqNUtgCNLGtuNMoThF94LJ81euvr0g==;Z8TfyHbGjFIs5+46S8ALAC81PBP6C6+0lJVkDirRH9xc2oM6jH/ErN4u3RFZNX+6TggISo8069fXnCG7nfeiWu+Y6Cgl2z3IP9DjI+LSvcWVOj1tnxDRa86VWEpuNTFEw+Jh1dSDuVjUjC6ZCKyOwcTskh6BuNzJI66jDu5NcaFRJzvu/IN3dXPoaae/7Tk3WRXBA01lSjWGvCGmO41ORmJOHLERISPHPEiySqacRDqCfMWiHozC2OIAyl0n1tRqUoF0o+nUpua8qdWz+bj4tOX76ja7++cQLEzg8wCC3aqaZQPTBUacH3ei4XFz5FzbSBgtbaeIUVg6FUdX/4yKlA==";


        String result = execute(content, enAndDeNode);
        System.out.println(result);

    }

    public static String execute(String content, EnAndDeNode enAndDeNode) throws Exception {
        String en_decrypt = enAndDeNode.getEn_decrypt();
        String encrypt_type = enAndDeNode.getEncrypt_type();
        String decrypt_type = enAndDeNode.getDecrypt_type();
        String encryptCode = enAndDeNode.getEncrypt_code();
        String decryptCode = enAndDeNode.getDecrypt_code();
        String sign_code = enAndDeNode.getSign_code();
        String publicKey = enAndDeNode.getPublic_key();
        String privateKey = enAndDeNode.getPrivate_key();
        String countersign = enAndDeNode.getCountersign();
        String secret = enAndDeNode.getSecret();
        String result = "";
        String cs="";
        /* --------------------------加解密判断-----------------------------*/
       //使用枚举判断加解密
        EnAndDeEnum enAndDeEnum=EnAndDeEnum.fromValue(en_decrypt);

        if (enAndDeEnum == EnAndDeEnum.ENCRYPT) {
            if ("RSA".equals(encrypt_type)) {
                result = EncryptionUtils.encryptWithRSA(content, privateKey, decryptCode);
            } else if ("3DES".equals(encrypt_type)) {
                result = EncryptionUtils.encryptWith3DES(content, secret, decryptCode);
            } else if ("AES".equals(encrypt_type)) {  // 添加 AES 加密逻辑
                result = EncryptionUtils.encryptWithAES(content, secret);  // 需要实现 AES 加密方法
            }
        } else if (enAndDeEnum == EnAndDeEnum.DECRYPT) { // If decryption is required
            if ("RSA".equals(decrypt_type)) {
                result = EncryptionUtils.decryptWithRSA(content, privateKey, decryptCode);
            } else if ("3DES".equals(decrypt_type)) {
                result = EncryptionUtils.decryptWith3DES(content, secret, decryptCode);
            } else if ("AES".equals(decrypt_type)) {  // 添加 AES 解密逻辑
                result = EncryptionUtils.decryptWithAES(content, secret);  // 需要实现 AES 解密方法
            }
        }



//        /* --------------------------签名逻辑-----------------------------*/
//        // 使用从 countersign 字段获取的枚举值判断是否需要加签
//        if (EnAndDeEnum.fromValue(countersign) == EnAndDeEnum.SIGN_REQUIRED) {
//            // 如果需要加签，首先检查加签字段是否为空
//            if (sign_code != null && !sign_code.isEmpty()) {
//                // 加签字段不为空，进行加签操作
//                cs = signContent(content, generatePrivateKey(privateKey), sign_code);  // 对指定字段进行加签
//                System.out.println("加签后的内容: " + cs);
//            } else {
//                // 加签字段为空，提示并不进行加签
//                System.out.println("加签字段不能为空");
//            }
//        } else {
//            System.out.println("不需要加签");
//        }
//        // 无论是否加签，都进行签名生成
//        PrivateKey privateKeyObj = (PrivateKey) generatePrivateKey(privateKey);
//        String signature = sign(content, privateKeyObj);
//        System.out.println("签名: " + signature);
//
//        // 验证签名
//        PublicKey publicKeyObj = (PublicKey) generatePublicKey(publicKey);
//        boolean isVerified = verify(content, signature, publicKeyObj);
//        System.out.println("签名验证结果: " + isVerified);
        return result;
    }
}




