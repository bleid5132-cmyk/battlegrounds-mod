import numpy as np, sys, json
from PIL import Image, ImageDraw
S='/tmp/claude-0/-home-claude/cb9e77f1-b529-5428-b56f-ba9e74153286/scratchpad'
def mask_ref(im):
    a=np.asarray(im.convert('RGB')).astype(int)
    g=(a[...,1]>a[...,0]+50)&(a[...,1]>a[...,2]+60)
    m=~g; m[:24,:]=False   # чёрная полоса сверху
    return m
def edges(m):
    e=m&~(np.roll(m,1,0)&np.roll(m,-1,0)&np.roll(m,1,1)&np.roll(m,-1,1))
    e[0,:]=e[-1,:]=e[:,0]=e[:,-1]=False
    ys,xs=np.nonzero(e); return np.column_stack([xs,ys]).astype(float)
def fit(m, prev_dir=None, rng=np.random.default_rng(0)):
    P=edges(m)
    if len(P)<50: return None
    best=None
    for _ in range(3000):
        i,j=rng.integers(0,len(P),2); d=P[j]-P[i]; L=np.linalg.norm(d)
        if L<40: continue
        d/=L
        if d[0]>0: d=-d            # направление к стволу: влево
        if prev_dir is not None and np.degrees(np.arccos(np.clip(d@prev_dir,-1,1)))>25: continue
        n=np.array([-d[1],d[0]]); r=np.abs((P-P[i])@n)
        inl=P[r<1.5]
        # длина непрерывного отрезка вдоль линии
        s=np.sort((inl-P[i])@d); 
        gaps=np.diff(s); cuts=np.nonzero(gaps>6)[0]; segs=np.split(s,cuts+1)
        seg=max(segs,key=lambda q:q[-1]-q[0]); score=seg[-1]-seg[0]
        if best is None or score>best[0]: best=(score,d,P[i])
    score,d,p0=best
    # уточнение МНК по инлаерам
    n=np.array([-d[1],d[0]]); inl=P[np.abs((P-p0)@n)<1.5]
    c=inl.mean(0); u,sv,vt=np.linalg.svd(inl-c); d=vt[0]; d=-d if d[0]>0 else d
    ys,xs=np.nonzero(m); Q=np.column_stack([xs,ys]).astype(float)
    k=np.argmax(Q@d); tip=Q[k]
    ang=np.degrees(np.arctan2(-d[1],-d[0]))  # угол линии ствола к горизонту (как на экране, вверх-влево положителен)
    return dict(tip=tip.tolist(), d=d.tolist(), ang=float(np.degrees(np.arctan2(-d[1], -d[0]))), score=float(score), c=c.tolist())
if __name__=='__main__':
    W,H=960,540; out={}
    prev=None
    for k in range(1,100):
        t=round((k-1)*0.05,2)
        im=Image.open(f'{S}/vidhr/f{k:03d}.png'); m=mask_ref(im)
        r=fit(m, None); out[t]=r
    json.dump(out,open(f'{S}/ref_meas.json','w'))
    # контрольные листы
    for sh in range(0,99,12):
        sheet=Image.new('RGB',(480*4,270*3))
        for i,k in enumerate(range(sh+1,min(sh+13,100))):
            t=round((k-1)*0.05,2); im=Image.open(f'{S}/vidhr/f{k:03d}.png').convert('RGB'); d=ImageDraw.Draw(im); r=out[t]
            if r:
                tx,ty=r['tip']; dx,dy=r['d']
                d.line([(tx,ty),(tx-dx*600,ty-dy*600)],fill=(0,160,255),width=3)
                d.line([(tx-60,ty),(tx+60,ty)],fill=(255,255,0),width=3)
                d.text((10,20),f"{t:.2f} tip=({tx:.0f},{ty:.0f}) ang={r['ang']:.1f}",fill=(255,255,255))
            sheet.paste(im.resize((480,270)),((i%4)*480,(i//4)*270))
        sheet.save(f'{S}/refm{sh//12}.png')

def mag_dist(m, r, below=30):
    """Расстояние вдоль ствола от дульного среза до передней кромки того, что свисает ниже линии на below px (магазин)."""
    ys,xs=np.nonzero(m); Q=np.column_stack([xs,ys]).astype(float)
    d=np.array(r['d']); tip=np.array(r['tip']); n=np.array([d[1],-d[0]])   # n — «вниз» относительно ствола
    if n[1]<0: n=-n
    along=(tip-Q)@d; perp=(Q-tip)@n
    sel=(perp>below)&(along>0)
    if sel.sum()<20: return None
    return float(np.percentile(along[sel],1))
